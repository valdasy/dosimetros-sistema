package com.dosimetros.backend.informeisp.service;

import com.dosimetros.backend.informeisp.entity.IspClienteTecnologia;
import com.dosimetros.backend.informeisp.entity.IspCodigoServicio;
import com.dosimetros.backend.informeisp.entity.IspPersonaCodigo;
import com.dosimetros.backend.informeisp.model.*;
import com.dosimetros.backend.informeisp.repository.IspClienteTecnologiaRepository;
import com.dosimetros.backend.informeisp.repository.IspCodigoServicioRepository;
import com.dosimetros.backend.informeisp.repository.IspPersonaCodigoRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Motor de asignación de códigos (Etapas 1-3). Limpia y valida el informe,
 * asigna COD SERV / COD PRAC / COD CARGO y arma las filas de TOES y DOSIS.
 * Ver docs/INFORME_ISP.md.
 */
@Service
public class AsignacionCodigosService {

    private final IspCodigoServicioRepository codigoServicioRepo;
    private final IspPersonaCodigoRepository personaRepo;
    private final IspClienteTecnologiaRepository clienteTecRepo;

    // Tecnología por defecto cuando el cliente no está en la maestra.
    private static final Map<String, String> DEFAULT_TEC = Map.of(
            "DOSIMET", "TLD", "PHOTOMAT", "FILM");

    public AsignacionCodigosService(IspCodigoServicioRepository codigoServicioRepo,
                                    IspPersonaCodigoRepository personaRepo,
                                    IspClienteTecnologiaRepository clienteTecRepo) {
        this.codigoServicioRepo = codigoServicioRepo;
        this.personaRepo = personaRepo;
        this.clienteTecRepo = clienteTecRepo;
    }

    public ResultadoProceso procesar(String empresa, List<FilaInforme> filas) {
        ResultadoProceso res = new ResultadoProceso();
        res.empresa = empresa;
        res.filasTotales = filas.size();
        String empresaNorm = IspMappings.norm(empresa);

        // --- Maestras en memoria (evita consultas por fila) ---
        // Personas: solo las del laboratorio en proceso (match por empresa + RUT).
        Map<String, IspPersonaCodigo> personaMap = new HashMap<>();
        for (IspPersonaCodigo p : personaRepo.findAll()) {
            if (empresaNorm.equals(IspMappings.norm(p.getEmpresa()))) {
                personaMap.put(IspMappings.norm(p.getRut()), p);
            }
        }
        Map<String, String> clienteTecMap = new HashMap<>();
        for (IspClienteTecnologia t : clienteTecRepo.findAll()) {
            if (empresaNorm.equals(IspMappings.norm(t.getEmpresa()))) {
                clienteTecMap.put(IspMappings.norm(t.getRutEntidad()), t.getTecnologia());
            }
        }
        Map<String, Integer> codServMap = new HashMap<>();
        for (IspCodigoServicio c : codigoServicioRepo.findAll()) {
            if (empresaNorm.equals(IspMappings.norm(c.getEmpresa()))) {
                codServMap.put(claveServ(c.getTecnologia(), c.getMagnitud(), c.getPeriodicidad()), c.getCodigo());
            }
        }
        String tecDefault = DEFAULT_TEC.getOrDefault(empresaNorm, "TLD");

        // --- Procesamiento fila a fila ---
        Map<String, PersonaToes> personas = new LinkedHashMap<>();
        Set<String> clientesSinTec = new HashSet<>();

        for (FilaInforme f : filas) {
            String rut = IspMappings.norm(f.rut);
            String tipo = IspMappings.norm(f.tipoDosimetro);
            boolean esPersona = !IspMappings.vacio(f.rut);

            // Validaciones de coherencia (no bloquean; van a REVISION)
            if (esPersona && IspMappings.TIPOS_NO_PERSONA.contains(tipo)) {
                res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                        "PERSONA_TIPO_NO_PERSONAL",
                        "Persona con TipoDosimetro '" + f.tipoDosimetro + "'. Revisar en el software de origen."));
            }
            if (!esPersona && IspMappings.TIPOS_PERSONA.contains(tipo)) {
                res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                        "SINRUT_TIPO_PERSONAL",
                        "Sin RUT pero TipoDosimetro '" + f.tipoDosimetro + "'. Revisar en el software de origen."));
            }

            // Etapa 1: eliminar sin RUT o sin Dosímetro (silencioso), y no-personas
            if (IspMappings.vacio(f.rut) || IspMappings.vacio(f.dosimetro)
                    || IspMappings.TIPOS_NO_PERSONA.contains(tipo)) {
                res.filasEliminadas++;
                continue;
            }

            // Magnitud (imprescindible para COD SERV)
            String magnitud = IspMappings.magnitudDeUbicacion(f.ubicacion);
            if (magnitud == null) {
                res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                        "UBICACION_NO_MAPEA",
                        "Ubicacion '" + f.ubicacion + "' no corresponde a una magnitud. Fila no incluida."));
                res.filasEliminadas++;
                continue;
            }

            // Tecnología: 1) columna 'tecnologia' del informe, 2) maestra por
            // cliente, 3) default del laboratorio (se marca solo en el caso 3).
            String ent = IspMappings.norm(f.documentoCliente);
            String tec;
            if (!IspMappings.vacio(f.tecnologia)) {
                tec = IspMappings.norm(f.tecnologia);
            } else {
                tec = clienteTecMap.get(ent);
                if (tec == null) {
                    tec = tecDefault;
                    if (clientesSinTec.add(ent)) {
                        res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                                "CLIENTE_SIN_TECNOLOGIA",
                                "Cliente sin tecnología (ni en la columna 'tecnologia' ni en la maestra). "
                                        + "Se usa el default del laboratorio: " + tecDefault + "."));
                    }
                }
            }

            // COD SERV
            String periodo = IspMappings.periodicidadCodigo(f.periodicidad);
            Integer codServ = codServMap.get(claveServ(tec, magnitud, periodo));
            if (codServ == null) {
                res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                        "SIN_COD_SERV",
                        "No hay Código de Servicio para " + tec + " / " + magnitud + " / " + periodo + "."));
            }

            // COD PRAC / COD CARGO: match por (empresa + RUT) contra la maestra
            // del trimestre anterior. Sin match -> quedan en blanco y se listan
            // en REVISION para completarlos a mano.
            Integer codCargo = null;
            Integer codPrac = null;
            IspPersonaCodigo persona = personaMap.get(rut);
            if (persona != null) {
                codCargo = persona.getCodCargo();
                codPrac = persona.getCodPrac();
            } else {
                res.inconsistencias.add(new Inconsistencia(f.filaExcel, f.rut, f.usuario, f.cliente,
                        "PERSONA_SIN_CARGO_PRAC",
                        "RUT no está en la maestra de " + empresa + " (trimestre anterior). "
                                + "COD CARGO y COD PRAC quedan en blanco para completar manualmente."));
            }

            // Dosis + OBSERVA
            String valorDosis = IspMappings.columnaDosisDeMagnitud(
                    magnitud, new IspMappings.FilaDosisInput(f.dosisProfundidad, f.dosisPiel, f.dosisCristalino));
            IspMappings.DosisIsp d = IspMappings.interpretarDosis(valorDosis);

            FilaDosis fd = new FilaDosis();
            fd.cliente = f.cliente;
            fd.area = f.area;
            fd.run = f.rut;
            fd.codServ = codServ;
            fd.rutEntidad = f.documentoCliente;
            fd.codPrac = codPrac;
            fd.codCargo = codCargo;
            fd.fechaInicio = f.fechaInicio;
            fd.fechaFin = f.fechaFin;
            fd.dosis = d.dosis;
            fd.observa = d.observa;
            res.dosis.add(fd);

            personas.computeIfAbsent(rut,
                    k -> new PersonaToes(f.rut, f.usuario, IspMappings.sexoIsp(f.genero)));
            res.filasProcesadas++;
        }

        // Orden de salida: DOSIS por Cliente y luego RUN; TOES por nombre.
        res.dosis.sort(Comparator
                .comparing((FilaDosis x) -> IspMappings.norm(x.cliente))
                .thenComparing(x -> IspMappings.norm(x.run)));
        res.toes = new ArrayList<>(personas.values());
        res.toes.sort(Comparator.comparing(p -> IspMappings.norm(p.nombre)));
        res.personasUnicas = personas.size();
        return res;
    }

    private static String claveServ(String tec, String magnitud, String periodo) {
        return tec + "|" + magnitud + "|" + periodo;
    }
}
