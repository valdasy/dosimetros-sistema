package com.dosimetros.backend.comparador.service;

import com.dosimetros.backend.comparador.model.UserRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de comparación entre listado del software y listado del cliente.
 *
 * Lógica de matching, siempre en este orden jerárquico:
 *   1) SEDE con SEDE
 *   2) ÁREA con ÁREA
 *   3) RUT con RUT
 *   4) NOMBRE + APELLIDO PATERNO + APELLIDO MATERNO con NOMBRE + APELLIDO PATERNO + APELLIDO MATERNO
 *      (usado para desambiguar RUT duplicados dentro de la misma sede/área, y como
 *      alternativa cuando el RUT es inválido o no hay match exacto de RUT)
 *   - Dosímetros especiales (CONTROL/AMBIENTAL/REFERENCIA): clave = SEDE + AREA + TIPO_DOSIMETRO + NOMBRE
 *     Si solo están en software → MANTENER con alerta "Cliente olvidó incluirlo"
 */
@Service
@RequiredArgsConstructor
public class ComparatorService {

    private final NormalizationService normalizationService;
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    private static final double AREA_SIMILARITY_THRESHOLD = 0.85;
    private static final double NAME_MISMATCH_ALERT_THRESHOLD = 0.5;

    public List<UserRecord> compare(List<UserRecord> softwareRecords, List<UserRecord> clientRecords) {
        List<UserRecord> results = new ArrayList<>();

        // Separar dosímetros especiales (sin RUT) de usuarios normales
        List<UserRecord> swEspeciales = softwareRecords.stream()
                .filter(UserRecord::esDosimetroEspecial)
                .collect(Collectors.toList());
        List<UserRecord> swUsuarios = softwareRecords.stream()
                .filter(r -> !r.esDosimetroEspecial())
                .collect(Collectors.toList());

        List<UserRecord> clEspeciales = clientRecords.stream()
                .filter(c -> esDosimetroEspecialCliente(c))
                .collect(Collectors.toList());
        List<UserRecord> clUsuarios = clientRecords.stream()
                .filter(c -> !esDosimetroEspecialCliente(c))
                .collect(Collectors.toList());

        // Procesar USUARIOS NORMALES
        Map<String, List<UserRecord>> duplicadosCliente = detectarDuplicadosCliente(clUsuarios);
        Set<UserRecord> clientMatched = new HashSet<>();
        Set<UserRecord> clientVerificar = new HashSet<>();

        for (UserRecord sw : swUsuarios) {
            String rutSw = sw.getRut();
            String areaSw = sw.getArea();
            String sedeSw = sw.getSede() != null ? sw.getSede().toUpperCase().trim() : "";
            String tipoSw = nvl(sw.getUbicacion()).toUpperCase().trim();

            // RUT inválido en software → intentar match por nombre en misma sede/área
            if (!normalizationService.isValidRut(rutSw)) {
                List<UserRecord> mismaSedeAreaInv = clUsuarios.stream()
                        .filter(c -> !clientMatched.contains(c))
                        .filter(c -> sedesMatch(sedeSw, c.getSedeCliente()))
                        .filter(c -> areasMatch(areaSw, c.getAreaCliente()))
                        .collect(Collectors.toList());

                Optional<UserRecord> nombreMatch = findByFuzzyName(sw.getUsuario(), mismaSedeAreaInv);
                if (nombreMatch.isPresent()) {
                    UserRecord m = nombreMatch.get();
                    clientVerificar.add(m);
                    sw.setResultado(UserRecord.ComparisonResult.VERIFICAR);
                    sw.setNombreCompletoCliente(m.getNombreCompletoCliente());
                    sw.setRutCliente(m.getRutCliente());
                    sw.setAreaCliente(m.getAreaCliente());
                    sw.setSedeCliente(m.getSedeCliente());
                    sw.setUbicacionCliente(m.getUbicacionCliente());
                    sw.setAlerta("⚠ VERIFICAR: RUT inválido en software (" + rutSw + ") — "
                            + "Se encontró usuario con nombre similar en el cliente: "
                            + m.getNombreCompletoCliente() + " (RUT cliente: " + m.getRutCliente() + ")");
                } else {
                    sw.setResultado(UserRecord.ComparisonResult.QUITAR);
                    sw.setAlerta("⚠ RUT INVÁLIDO EN SOFTWARE: " + rutSw + " — corregir en el software");
                }
                results.add(sw);
                continue;
            }

            // Buscar match exacto: SEDE con SEDE, luego ÁREA con ÁREA, luego RUT con RUT
            // (+ tipo dosímetro). Si el mismo RUT aparece más de una vez dentro de la
            // misma sede/área (RUT duplicado en el cliente), se desambigua comparando
            // NOMBRE + APELLIDO PATERNO + APELLIDO MATERNO.
            List<UserRecord> exactCandidates = clUsuarios.stream()
                    .filter(c -> !clientMatched.contains(c))
                    .filter(c -> sedesMatch(sedeSw, c.getSedeCliente()))
                    .filter(c -> areasMatch(areaSw, c.getAreaCliente()))
                    .filter(c -> rutSw.equals(c.getRutCliente()))
                    .filter(c -> tiposMatch(tipoSw, c.getUbicacionCliente()))
                    .collect(Collectors.toList());

            if (!exactCandidates.isEmpty()) {
                // MANTENER - match completo
                UserRecord m = exactCandidates.size() == 1
                        ? exactCandidates.get(0)
                        : bestByName(sw.getUsuario(), exactCandidates);
                clientMatched.add(m);
                sw.setResultado(UserRecord.ComparisonResult.MANTENER);
                sw.setNombreCompletoCliente(m.getNombreCompletoCliente());
                sw.setRutCliente(m.getRutCliente());
                sw.setAreaCliente(m.getAreaCliente());
                sw.setSedeCliente(m.getSedeCliente());
                sw.setUbicacionCliente(m.getUbicacionCliente());

                List<String> alertas = new ArrayList<>();
                String key = rutSw + "|" + m.getAreaCliente() + "|" + nvl(m.getUbicacionCliente());
                if (duplicadosCliente.containsKey(key) && duplicadosCliente.get(key).size() > 1) {
                    alertas.add("⚠ RUT DUPLICADO EN CLIENTE (misma área/tipo)");
                }
                if (!normalizationService.isValidRut(m.getRutCliente())) {
                    alertas.add("⚠ RUT INVÁLIDO EN CLIENTE: " + m.getRutCliente());
                }
                if (nameScore(sw.getUsuario(), m.getNombreCompletoCliente()) < NAME_MISMATCH_ALERT_THRESHOLD) {
                    alertas.add("⚠ VERIFICAR NOMBRE: mismo RUT/sede/área pero nombre distinto — "
                            + "Software: [" + sw.getUsuario() + "] Cliente: [" + m.getNombreCompletoCliente() + "]");
                }
                if (!alertas.isEmpty()) sw.setAlerta(String.join(" | ", alertas));
                results.add(sw);
                continue;
            }

            // No hubo match exacto - buscar candidatos dentro de misma sede+área
            List<UserRecord> mismaSedeArea = clUsuarios.stream()
                    .filter(c -> !clientMatched.contains(c))
                    .filter(c -> sedesMatch(sedeSw, c.getSedeCliente()))
                    .filter(c -> areasMatch(areaSw, c.getAreaCliente()))
                    .collect(Collectors.toList());

            // Paso A: RUT similar (mismo cuerpo, DV distinto)
            Optional<UserRecord> rutSimilar = findByRutSimilar(rutSw, mismaSedeArea);
            if (rutSimilar.isPresent()) {
                UserRecord m = rutSimilar.get();
                clientVerificar.add(m);
                sw.setResultado(UserRecord.ComparisonResult.VERIFICAR);
                sw.setNombreCompletoCliente(m.getNombreCompletoCliente());
                sw.setRutCliente(m.getRutCliente());
                sw.setAreaCliente(m.getAreaCliente());
                sw.setSedeCliente(m.getSedeCliente());
                sw.setUbicacionCliente(m.getUbicacionCliente());
                sw.setAlerta("⚠ VERIFICAR: RUT similar en misma sede/área — "
                        + "Software: [" + sw.getUsuario() + " | RUT: " + rutSw + " | Ubicación: " + tipoSw + "] "
                        + "Cliente: [" + m.getNombreCompletoCliente() + " | RUT: " + m.getRutCliente() + " | Ubicación: " + nvl(m.getUbicacionCliente()) + "] "
                        + "— Posible error de tipeo en dígito verificador");
                results.add(sw);
                continue;
            }

            // Paso B: Nombre similar (mismas palabras, sin importar orden)
            Optional<UserRecord> nombreSimilar = findByFuzzyName(sw.getUsuario(), mismaSedeArea);
            if (nombreSimilar.isPresent()) {
                UserRecord m = nombreSimilar.get();
                clientVerificar.add(m);
                sw.setResultado(UserRecord.ComparisonResult.VERIFICAR);
                sw.setNombreCompletoCliente(m.getNombreCompletoCliente());
                sw.setRutCliente(m.getRutCliente());
                sw.setAreaCliente(m.getAreaCliente());
                sw.setSedeCliente(m.getSedeCliente());
                sw.setUbicacionCliente(m.getUbicacionCliente());
                sw.setAlerta("⚠ VERIFICAR: Nombre similar en misma sede/área pero RUT diferente — "
                        + "Software: [" + sw.getUsuario() + " | RUT: " + rutSw + "] "
                        + "Cliente: [" + m.getNombreCompletoCliente() + " | RUT: " + m.getRutCliente() + "]");
                results.add(sw);
                continue;
            }

            // Paso C: ¿Mismo RUT existe pero con tipo de dosímetro distinto?
            Optional<UserRecord> mismoUsuarioOtroTipo = mismaSedeArea.stream()
                    .filter(c -> rutSw.equals(c.getRutCliente()))
                    .findFirst();
            if (mismoUsuarioOtroTipo.isPresent()) {
                sw.setResultado(UserRecord.ComparisonResult.QUITAR);
                sw.setAlerta("❌ NO ENCONTRADO con ubicación '" + tipoSw + "' — "
                        + "El usuario sí está en el listado pero con otra ubicación");
                results.add(sw);
                continue;
            }

            // Nada coincide
            sw.setResultado(UserRecord.ComparisonResult.QUITAR);
            sw.setAlerta("❌ NO ENCONTRADO EN LISTADO CLIENTE");
            results.add(sw);
        }

        // Usuarios del cliente no matcheados → NUEVO
        for (UserRecord c : clUsuarios) {
            if (clientMatched.contains(c)) continue;
            if (clientVerificar.contains(c)) continue;
            if (c.getRutCliente().isBlank() && c.getNombreCompletoCliente().isBlank()) continue;
            if ("REFERENCIA".equalsIgnoreCase(c.getNombreCompletoCliente())) continue;

            List<String> alertas = new ArrayList<>();
            String key = c.getRutCliente() + "|" + c.getAreaCliente() + "|" + nvl(c.getUbicacionCliente());
            if (duplicadosCliente.containsKey(key) && duplicadosCliente.get(key).size() > 1) {
                alertas.add("⚠ RUT DUPLICADO EN CLIENTE");
            }
            if (!c.getRutCliente().isBlank() && !normalizationService.isValidRut(c.getRutCliente())) {
                alertas.add("⚠ RUT INVÁLIDO: " + c.getRutCliente());
            }

            UserRecord nuevo = UserRecord.builder()
                    .rutCliente(c.getRutCliente())
                    .nombreCompletoCliente(c.getNombreCompletoCliente())
                    .nombreCliente(c.getNombreCliente())
                    .apellidoPaternoCliente(c.getApellidoPaternoCliente())
                    .apellidoMaternoCliente(c.getApellidoMaternoCliente())
                    .areaCliente(c.getAreaCliente())
                    .sedeCliente(c.getSedeCliente())
                    .ubicacionCliente(c.getUbicacionCliente())
                    .resultado(UserRecord.ComparisonResult.NUEVO)
                    .alerta(alertas.isEmpty() ? "🆕 USUARIO NUEVO" : String.join(" | ", alertas))
                    .build();
            results.add(nuevo);
        }

        // ── DOSÍMETROS ESPECIALES (sin RUT) ──
        // Match por sede + área + tipo
        Set<UserRecord> espMatched = new HashSet<>();
        for (UserRecord sw : swEspeciales) {
            String areaSw = sw.getArea();
            String sedeSw = sw.getSede() != null ? sw.getSede().toUpperCase().trim() : "";
            String tipoSw = nvl(sw.getUbicacion()).toUpperCase().trim();

            String nombreSw = nvl(sw.getUsuario()).toUpperCase().trim();
            // Para especiales usamos comparación EXACTA de sede y área (no fuzzy),
            // porque nombres como 'OFICINA SAG ANTOFAGASTA' y 'OFICINA SAG COPIAPO'
            // son muy similares y el fuzzy los cruzaría erróneamente.
            Optional<UserRecord> match = clEspeciales.stream()
                    .filter(c -> !espMatched.contains(c))
                    .filter(c -> sedesMatchExacto(sedeSw, c.getSedeCliente()))
                    .filter(c -> areasMatchExacto(areaSw, c.getAreaCliente()))
                    .filter(c -> tiposMatch(tipoSw, c.getUbicacionCliente()))
                    .filter(c -> nombresEspecialesMatch(nombreSw, c.getNombreCompletoCliente()))
                    .findFirst();

            if (match.isPresent()) {
                UserRecord m = match.get();
                espMatched.add(m);
                sw.setResultado(UserRecord.ComparisonResult.MANTENER);
                sw.setUbicacionCliente(m.getUbicacionCliente());
                sw.setAreaCliente(m.getAreaCliente());
                sw.setSedeCliente(m.getSedeCliente());
                sw.setNombreCompletoCliente(m.getNombreCompletoCliente());
                sw.setRutCliente(m.getRutCliente());
            } else {
                // El cliente lo olvidó pero debe mantenerse
                sw.setResultado(UserRecord.ComparisonResult.MANTENER);
                String etiqueta = sw.getUsuario() != null && !sw.getUsuario().isBlank()
                        ? sw.getUsuario() + " (" + tipoSw + ")"
                        : "DOSÍMETRO " + tipoSw;
                sw.setAlerta("⚠ Cliente olvidó incluir " + etiqueta + " — mantener en el servicio");
            }
            // Asegurar que tenga un nombre visible
            if (sw.getUsuario() == null || sw.getUsuario().isBlank()) {
                sw.setUsuario("DOSÍMETRO " + tipoSw);
            }
            results.add(sw);
        }

        // Dosímetros especiales del cliente no matcheados → NUEVO
        for (UserRecord c : clEspeciales) {
            if (espMatched.contains(c)) continue;
            String tipoC = nvl(c.getUbicacionCliente()).toUpperCase().trim();
            String nombreCl = nvl(c.getNombreCompletoCliente()).trim();
            String nombreFinal = !nombreCl.isBlank() ? nombreCl : "DOSÍMETRO " + tipoC;
            UserRecord nuevo = UserRecord.builder()
                    .nombreCompletoCliente(nombreFinal)
                    .areaCliente(c.getAreaCliente())
                    .sedeCliente(c.getSedeCliente())
                    .ubicacionCliente(tipoC)
                    .resultado(UserRecord.ComparisonResult.NUEVO)
                    .alerta("🆕 Dosímetro especial nuevo en listado cliente")
                    .build();
            results.add(nuevo);
        }

        // Ordenar
        results.sort(Comparator
                .comparing((UserRecord r) -> {
                    String sede = (r.getSede() != null && !r.getSede().isBlank()) ? r.getSede() : nvl(r.getSedeCliente());
                    return sede;
                })
                .thenComparing(r -> {
                    String area = (r.getArea() != null && !r.getArea().isBlank()) ? r.getArea() : nvl(r.getAreaCliente());
                    return area;
                })
                .thenComparing(r -> switch (r.getResultado()) {
                    case MANTENER  -> 0;
                    case VERIFICAR -> 1;
                    case QUITAR    -> 2;
                    case NUEVO     -> 3;
                }));

        return results;
    }

    public Map<String, Long> buildSummary(List<UserRecord> results) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) results.size());
        summary.put("mantener", results.stream().filter(r -> r.getResultado() == UserRecord.ComparisonResult.MANTENER).count());
        summary.put("quitar",   results.stream().filter(r -> r.getResultado() == UserRecord.ComparisonResult.QUITAR).count());
        summary.put("nuevo",    results.stream().filter(r -> r.getResultado() == UserRecord.ComparisonResult.NUEVO).count());
        summary.put("verificar",results.stream().filter(r -> r.getResultado() == UserRecord.ComparisonResult.VERIFICAR).count());
        summary.put("conAlertas", results.stream().filter(r -> r.getAlerta() != null && !r.getAlerta().isBlank()).count());
        return summary;
    }

    // ─── HELPERS ───

    private boolean esDosimetroEspecialCliente(UserRecord c) {
        // Solo RUT AUSENTE (vacío) → dosímetro especial.
        // RUT INVÁLIDO → es un usuario normal mal escrito, se compara por nombre.
        if (c.getRutCliente() == null || c.getRutCliente().isBlank()) return true;
        String t = nvl(c.getUbicacionCliente()).toUpperCase();
        return t.contains("CONTROL") || t.contains("AMBIENTAL") || t.contains("REFERENCIA");
    }

    private Map<String, List<UserRecord>> detectarDuplicadosCliente(List<UserRecord> registros) {
        return registros.stream()
                .filter(r -> !r.getRutCliente().isBlank())
                .collect(Collectors.groupingBy(
                        r -> r.getRutCliente() + "|" + r.getAreaCliente() + "|" + nvl(r.getUbicacionCliente())
                ));
    }

    private Optional<UserRecord> findByRutSimilar(String rutSw, List<UserRecord> candidatos) {
        if (rutSw == null || !rutSw.contains("-")) return Optional.empty();
        String cuerpoSw = rutSw.split("-")[0];
        return candidatos.stream()
                .filter(c -> {
                    String rutC = c.getRutCliente();
                    if (rutC == null || !rutC.contains("-")) return false;
                    String cuerpoC = rutC.split("-")[0];
                    return cuerpoSw.equals(cuerpoC) && !rutSw.equals(rutC);
                })
                .findFirst();
    }

    /**
     * Busca un usuario con nombre similar. Considera coincidencia exacta cuando
     * todas las palabras del software aparecen en el cliente (sin importar orden).
     */
    private Optional<UserRecord> findByFuzzyName(String nombreSw, List<UserRecord> candidatos) {
        if (nombreSw == null || nombreSw.isBlank()) return Optional.empty();

        UserRecord bestMatch = bestByName(nombreSw, candidatos);
        if (bestMatch != null && nameScore(nombreSw, bestMatch.getNombreCompletoCliente()) >= 0.80) {
            return Optional.of(bestMatch);
        }
        return Optional.empty();
    }

    /**
     * De entre varios candidatos con el mismo RUT + SEDE + ÁREA (p.ej. RUT duplicado
     * en el cliente), elige el que tiene el nombre (NOMBRE + APELLIDO PATERNO +
     * APELLIDO MATERNO) más parecido al del software.
     */
    private UserRecord bestByName(String nombreSw, List<UserRecord> candidatos) {
        UserRecord bestMatch = null;
        double bestScore = -1;
        for (UserRecord c : candidatos) {
            double score = nameScore(nombreSw, c.getNombreCompletoCliente());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = c;
            }
        }
        return bestMatch != null ? bestMatch : (candidatos.isEmpty() ? null : candidatos.get(0));
    }

    /**
     * Calcula la similitud entre el nombre completo del software y el nombre
     * completo (nombre + apellido paterno + apellido materno) del cliente.
     */
    private double nameScore(String nombreSw, String nombreCliente) {
        if (nombreSw == null || nombreSw.isBlank() || nombreCliente == null || nombreCliente.isBlank()) return 0;

        Set<String> palabrasSw = palabrasSignificativas(nombreSw);
        Set<String> palabrasCl = palabrasSignificativas(nombreCliente);
        if (palabrasSw.isEmpty() || palabrasCl.isEmpty()) return 0;

        // Contar palabras coincidentes (con tolerancia fuzzy)
        int coincidencias = 0;
        for (String p : palabrasSw) {
            for (String pc : palabrasCl) {
                if (p.equals(pc) || jaroWinkler.apply(p, pc) >= 0.92) {
                    coincidencias++;
                    break;
                }
            }
        }

        int minPalabras = Math.min(palabrasSw.size(), palabrasCl.size());
        double simPalabras = (minPalabras > 0 && coincidencias >= 2)
                ? (double) coincidencias / minPalabras
                : 0;

        double simDirecta = jaroSimilarity(nombreSw, nombreCliente);
        return Math.max(simDirecta, simPalabras);
    }

    private Set<String> palabrasSignificativas(String nombre) {
        if (nombre == null) return Collections.emptySet();
        return Arrays.stream(nombre.split("\\s+"))
                .map(String::trim)
                .filter(p -> p.length() >= 3)
                .collect(Collectors.toSet());
    }

    private boolean sedesMatch(String sede1, String sede2) {
        if (sede1 == null || sede1.isBlank()) return true;
        if (sede2 == null || sede2.isBlank()) return true;
        String s1 = normalizationService.normalizeName(sede1);
        String s2 = normalizationService.normalizeName(sede2);
        if (s1.equals(s2)) return true;
        return jaroSimilarity(s1, s2) >= AREA_SIMILARITY_THRESHOLD;
    }

    private boolean areasMatch(String area1, String area2) {
        if (area1 == null || area2 == null) return false;
        if (area1.equalsIgnoreCase(area2)) return true;
        return jaroSimilarity(area1.toUpperCase(), area2.toUpperCase()) >= AREA_SIMILARITY_THRESHOLD;
    }

    /**
     * Similitud de Jaro "pura" (sin el bono de prefijo de Jaro-Winkler).
     *
     * Se usa deliberadamente en vez de JaroWinklerSimilarity para SEDE/ÁREA/NOMBRE
     * completos: el bono de prefijo de Winkler infla el score cuando dos strings
     * distintos comparten un prefijo largo (p.ej. "OFICINA SAG LOS ANDES" vs
     * "OFICINA SAG COQUIMBO" da 0.85 con Winkler — por encima del umbral — aunque
     * son sedes/áreas completamente distintas). Jaro-Winkler sí se sigue usando
     * para comparar palabras individuales cortas (nombres, tipos de dosímetro),
     * donde ese bono de prefijo es apropiado para tolerar errores de tipeo.
     */
    private double jaroSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDistance = Math.max(0, Math.max(len1, len2) / 2 - 1);
        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;

        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2;

        return ((matches / (double) len1) + (matches / (double) len2) + ((matches - transpositions) / matches)) / 3.0;
    }

    /**
     * Match de nombres para dosímetros especiales (sin RUT).
     * Compara nombre exacto normalizado, o considera match si ambos están vacíos.
     */
    private boolean nombresEspecialesMatch(String nombre1, String nombre2) {
        String n1 = nvl(nombre1).toUpperCase().trim();
        String n2 = nvl(nombre2).toUpperCase().trim();
        if (n1.isBlank() && n2.isBlank()) return true;
        if (n1.isBlank() || n2.isBlank()) return false;
        if (n1.equals(n2)) return true;
        // Tolerancia fuzzy para nombres como "DISPONIBLE 1" vs "DISPONIBLE1"
        return jaroWinkler.apply(n1, n2) >= 0.90;
    }

    /**
     * Dos tipos de dosímetro hacen match si son iguales o muy similares.
     * Si el cliente no especifica tipo (vacío), se considera match para no bloquear.
     */
    private boolean tiposMatch(String tipo1, String tipo2) {
        if (tipo1 == null || tipo1.isBlank()) return true;
        if (tipo2 == null || tipo2.isBlank()) return true; // cliente no especificó tipo
        String t1 = normalizationService.normalizeName(tipo1);
        String t2 = normalizationService.normalizeName(tipo2);
        if (t1.equals(t2)) return true;
        // Casos comunes equivalentes
        if (t1.contains("CUERPO") && t2.contains("CUERPO")) return true;
        if (t1.contains("ANILLO") && t2.contains("ANILLO")) return true;
        if (t1.contains("PULSERA") && t2.contains("PULSERA")) return true;
        if (t1.contains("CRISTALINO") && t2.contains("CRISTALINO")) return true;
        if (t1.contains("ABDOMINAL") && t2.contains("ABDOMINAL")) return true;
        return jaroWinkler.apply(t1, t2) >= 0.85;
    }

    private String nvl(String s) { return s != null ? s : ""; }
    /**
     * Match EXACTO de sedes (sin fuzzy). Usado para dosímetros especiales.
     */
    private boolean sedesMatchExacto(String sede1, String sede2) {
        String s1 = normalizationService.normalizeName(nvl(sede1));
        String s2 = normalizationService.normalizeName(nvl(sede2));
        if (s1.isBlank() && s2.isBlank()) return true;
        return s1.equals(s2);
    }

    /**
     * Match EXACTO de áreas (sin fuzzy). Usado para dosímetros especiales.
     */
    private boolean areasMatchExacto(String area1, String area2) {
        String a1 = normalizationService.normalizeArea(nvl(area1));
        String a2 = normalizationService.normalizeArea(nvl(area2));
        if (a1.isBlank() && a2.isBlank()) return true;
        return a1.equals(a2);
    }
}
