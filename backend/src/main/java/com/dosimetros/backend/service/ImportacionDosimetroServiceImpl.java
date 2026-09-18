package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.dosimetro.ActualizacionStockResponse;
import com.dosimetros.backend.dto.dosimetro.ImportacionDosimetrosResponse;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Tarea;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.TareaRepository;
import com.dosimetros.backend.repository.TipoDosimetroRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Columnas de la plantilla PLANTILLA_CARGA_MASIVA
 * (fila 0 = encabezado, datos desde fila 1):
 *   A(0) = numero_dosimetro -> dosimetro.numero        (numerico, obligatorio)
 *   B(1) = tipo_dosimetro   -> tipoDosimetro.nombre    (texto: TLD, OSL, Cristal; obligatorio)
 *   C(2) = tipo_porta       -> tipoPorta.nombre        (texto, opcional)
 *   D(3) = numero_tarea     -> tarea.numeroTarea       (texto, opcional; vacío para OSL)
 *   E(4) = numero_bandeja   -> dosimetro.numeroBandeja (numerico, opcional; vacío para OSL)
 *   F(5) = slot_bandeja     -> dosimetro.slotBandeja   (numerico, opcional; vacío para OSL)
 */
@Service
public class ImportacionDosimetroServiceImpl implements ImportacionDosimetroService {

    private static final int COL_NUMDOSIM        = 0;
    private static final int COL_TIPO_DOSIMETRO  = 1;
    private static final int COL_TIPO_PORTA      = 2;
    private static final int COL_NUM_REPOSITORIO = 3;
    private static final int COL_NUM_BANDEJA     = 4;
    private static final int COL_SLOT_BANDEJA    = 5;

    // Límite de filas de datos por archivo (evita agotar memoria con archivos gigantes).
    private static final int MAX_FILAS = 200_000;

    static {
        // Defensa ante "zip bombs": exige un ratio mínimo de compresión al descomprimir.
        ZipSecureFile.setMinInflateRatio(0.01);
    }

    private final DosimetroRepository dosimetroRepository;
    private final TipoDosimetroRepository tipoDosimetroRepository;
    private final TipoPortaRepository tipoPortaRepository;
    private final TareaRepository tareaRepository;

    public ImportacionDosimetroServiceImpl(DosimetroRepository dosimetroRepository,
                                           TipoDosimetroRepository tipoDosimetroRepository,
                                           TipoPortaRepository tipoPortaRepository,
                                           TareaRepository tareaRepository) {
        this.dosimetroRepository = dosimetroRepository;
        this.tipoDosimetroRepository = tipoDosimetroRepository;
        this.tipoPortaRepository = tipoPortaRepository;
        this.tareaRepository = tareaRepository;
    }

    // Datos de una fila ya parseados y resueltos a entidades.
    private record FilaParseada(int numero, TipoDosimetro tipoDosimetro, TipoPorta tipoPorta,
                                Tarea tarea, Integer numeroBandeja, Integer slotBandeja) {
    }

    @Override
    @Transactional
    public ImportacionDosimetrosResponse importarExcel(MultipartFile file) {
        validarArchivo(file);

        ImportacionDosimetrosResponse response = new ImportacionDosimetrosResponse();
        List<String> errores = new ArrayList<>();
        // Guarda los saves hasta validar todo el archivo: solo se ejecutan si no
        // hubo ningún error (todo o nada).
        List<Runnable> acciones = new ArrayList<>();
        int exitosas = 0;
        int fallidas = 0;
        int omitidas = 0;
        // Clave por (número + tecnología): un mismo número puede existir en OSL y TLD.
        Set<String> clavesEnArchivo = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int totalFilas = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // saltar encabezado
                if (esFilaVacia(row)) continue;

                totalFilas++;
                if (totalFilas > MAX_FILAS) {
                    throw new IllegalArgumentException(
                            "El archivo supera el máximo de " + MAX_FILAS + " filas permitidas");
                }
                int fila = row.getRowNum() + 1;

                try {
                    FilaParseada f = parseFila(row, formatter);

                    // Mismo número+tecnología repetido dentro del archivo.
                    if (!clavesEnArchivo.add(f.numero() + "|" + f.tipoDosimetro().getId())) {
                        omitidas++;
                        errores.add("Fila " + fila + ": número " + f.numero() + " ("
                                + f.tipoDosimetro().getNombre() + ") repetido dentro del archivo (omitido)");
                        continue;
                    }
                    // Ya existe ese número en esa tecnología: no se duplica el dosímetro.
                    if (dosimetroRepository.existsByNumeroAndTipoDosimetroId(
                            f.numero(), f.tipoDosimetro().getId())) {
                        omitidas++;
                        errores.add("Fila " + fila + ": número " + f.numero() + " ("
                                + f.tipoDosimetro().getNombre() + ") ya existe en el sistema (omitido, no se duplica)");
                        continue;
                    }

                    Dosimetro nuevo = nuevoDosimetro(f);
                    acciones.add(() -> dosimetroRepository.save(nuevo));
                    exitosas++;

                } catch (Exception e) {
                    fallidas++;
                    errores.add("Fila " + fila + ": " + e.getMessage());
                }
            }

            // Todo o nada: solo se persiste si el archivo no tenía ningún error;
            // en caso contrario no se guarda nada y se conserva el detalle de
            // errores para mostrarlo al usuario.
            if (errores.isEmpty()) {
                acciones.forEach(Runnable::run);
            } else {
                response.setAplicado(false);
                exitosas = 0;
            }

            response.setTotalFilas(totalFilas);
            response.setExitosas(exitosas);
            response.setFallidas(fallidas);
            response.setOmitidas(omitidas);
            response.setErrores(errores);

        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo Excel", e);
        }

        return response;
    }

    @Override
    @Transactional
    public ActualizacionStockResponse actualizarStockExcel(MultipartFile file) {
        validarArchivo(file);

        ActualizacionStockResponse response = new ActualizacionStockResponse();
        List<String> errores = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        // Guarda los cambios hasta validar todo el archivo: solo se ejecutan si
        // no hubo ningún error (todo o nada).
        List<Runnable> acciones = new ArrayList<>();
        int creados = 0;
        int actualizados = 0;
        int sinCambios = 0;
        int fallidas = 0;
        int duplicados = 0;
        // Clave por (número + tecnología): un mismo número puede existir en OSL y TLD.
        Set<String> clavesEnArchivo = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int totalFilas = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (esFilaVacia(row)) continue;

                totalFilas++;
                if (totalFilas > MAX_FILAS) {
                    throw new IllegalArgumentException(
                            "El archivo supera el máximo de " + MAX_FILAS + " filas permitidas");
                }
                int fila = row.getRowNum() + 1;

                try {
                    FilaParseada f = parseFila(row, formatter);

                    if (!clavesEnArchivo.add(f.numero() + "|" + f.tipoDosimetro().getId())) {
                        fallidas++;
                        errores.add("Fila " + fila + ": número " + f.numero() + " ("
                                + f.tipoDosimetro().getNombre() + ") repetido dentro del archivo (omitido)");
                        continue;
                    }

                    // Se busca por (número + tecnología): así un OSL y un TLD con el
                    // mismo número se tratan por separado y no como duplicado.
                    List<Dosimetro> existentes = dosimetroRepository
                            .findByNumeroAndTipoDosimetroIdOrderByIdAsc(f.numero(), f.tipoDosimetro().getId());

                    if (existentes.isEmpty()) {
                        // No existe → se crea (upsert: insert).
                        Dosimetro nuevo = nuevoDosimetro(f);
                        acciones.add(() -> dosimetroRepository.save(nuevo));
                        creados++;
                    } else if (existentes.size() > 1) {
                        // Número duplicado en el sistema: no se puede actualizar sin ambigüedad.
                        fallidas++;
                        errores.add("Fila " + fila + ": número " + f.numero() + " aparece "
                                + existentes.size() + " veces en el sistema; no se actualiza automáticamente");
                    } else {
                        Dosimetro d = existentes.get(0);
                        String estado = d.getEstado();
                        boolean dadoDeBaja = "baja".equalsIgnoreCase(estado);
                        boolean estabaAsignado = "asignado".equalsIgnoreCase(estado);
                        boolean estabaExtraviado = "extraviado".equalsIgnoreCase(estado);

                        boolean armadoEnOtraTarea = d.getTarea() != null && f.tarea() != null
                                && !Objects.equals(d.getTarea().getId(), f.tarea().getId());

                        // Un extraviado que reaparece en la carga siempre se procesa
                        // (aunque el armado coincida) para recuperarlo a disponible.
                        if (mismoArmado(d, f) && !estabaExtraviado) {
                            sinCambios++;
                        } else if (dadoDeBaja) {
                            // Un dosímetro dado de baja no se reactiva por archivo;
                            // requiere una acción explícita.
                            fallidas++;
                            errores.add("Fila " + fila + ": número " + f.numero()
                                    + " está dado de baja; no se actualiza su armado por archivo");
                        } else if (!estabaAsignado && !estabaExtraviado && armadoEnOtraTarea) {
                            // Mismo número, disponible y ya armado en OTRA tarea: es un
                            // posible duplicado físico (dos dosímetros con el mismo
                            // número). No se sobrescribe el existente: se carga una
                            // copia y se avisa para resolverlo en el módulo Duplicados
                            // (sacar uno y dejarlo de backup).
                            Dosimetro copia = nuevoDosimetro(f);
                            acciones.add(() -> dosimetroRepository.save(copia));
                            duplicados++;
                            alertas.add("Fila " + fila + ": el dosímetro " + f.numero()
                                    + " ya existe armado en la tarea " + d.getTarea().getNumeroTarea()
                                    + " y el archivo lo trae en la tarea " + f.tarea().getNumeroTarea()
                                    + ". Se cargó como copia (posible duplicado). Revísalo en el módulo"
                                    + " Duplicados para conservar solo uno.");
                        } else {
                            // La mutación se difiere: solo se aplica si el archivo
                            // completo es válido.
                            acciones.add(() -> {
                                d.setTipoDosimetro(f.tipoDosimetro());
                                d.setTipoPorta(f.tipoPorta());
                                d.setTarea(f.tarea());
                                d.setNumeroBandeja(f.numeroBandeja());
                                d.setSlotBandeja(f.slotBandeja());
                                // Si venía asignado (volvió a la oficina) o estaba
                                // extraviado (reapareció en esta carga), se rearma y
                                // queda disponible. El historial se conserva.
                                if (estabaAsignado || estabaExtraviado) {
                                    d.setEstado("disponible");
                                }
                                dosimetroRepository.save(d);
                            });
                            actualizados++;
                        }
                    }

                } catch (Exception e) {
                    fallidas++;
                    errores.add("Fila " + fila + ": " + e.getMessage());
                }
            }

            // Todo o nada: solo se persiste si el archivo no tenía ningún error;
            // en caso contrario no se guarda nada y se conserva el detalle de
            // errores para mostrarlo al usuario. Las alertas (duplicados) no
            // bloquean, pero si el archivo se rechaza tampoco se aplicaron.
            if (errores.isEmpty()) {
                acciones.forEach(Runnable::run);
            } else {
                response.setAplicado(false);
                creados = 0;
                actualizados = 0;
                sinCambios = 0;
                duplicados = 0;
                alertas.clear();
            }

            response.setTotalFilas(totalFilas);
            response.setCreados(creados);
            response.setActualizados(actualizados);
            response.setSinCambios(sinCambios);
            response.setFallidas(fallidas);
            response.setDuplicados(duplicados);
            response.setErrores(errores);
            response.setAlertas(alertas);

        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo Excel", e);
        }

        return response;
    }

    @Override
    public byte[] plantillaExcel() {
        String[] cabeceras = {"numero_dosimetro", "tipo_dosimetro", "tipo_porta",
                "numero_tarea", "numero_bandeja", "slot_bandeja"};
        // Fila de ejemplo ficticia para guiar el llenado.
        String[] ejemplo = {"12345", "TLD", "Porta gringo", "1765", "3", "12"};

        // Opciones reales del sistema para los desplegables.
        List<String> tiposDosimetro = tipoDosimetroRepository.findAll().stream()
                .map(TipoDosimetro::getNombre).sorted().toList();
        List<String> tiposPorta = tipoPortaRepository.findAll().stream()
                .map(TipoPorta::getNombre).distinct().sorted().toList();

        // Última fila (0-indexada) a la que se aplica el desplegable.
        final int ULTIMA_FILA = 1000;

        try (Workbook wb = new XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Carga");

            Row header = sheet.createRow(0);
            CellStyle negrita = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            negrita.setFont(font);
            for (int i = 0; i < cabeceras.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cabeceras[i]);
                c.setCellStyle(negrita);
            }

            Row fila = sheet.createRow(1);
            for (int i = 0; i < ejemplo.length; i++) {
                fila.createCell(i).setCellValue(ejemplo[i]);
            }

            for (int i = 0; i < cabeceras.length; i++) sheet.autoSizeColumn(i);

            // Hoja oculta con las listas de opciones (evita el límite de 255
            // caracteres de las listas explícitas de validación).
            Sheet opciones = wb.createSheet("opciones");
            for (int i = 0; i < tiposDosimetro.size(); i++) {
                opciones.createRow(i).createCell(0).setCellValue(tiposDosimetro.get(i));
            }
            for (int i = 0; i < tiposPorta.size(); i++) {
                Row r = opciones.getRow(i);
                if (r == null) r = opciones.createRow(i);
                r.createCell(1).setCellValue(tiposPorta.get(i));
            }
            wb.setSheetHidden(wb.getSheetIndex("opciones"), true);

            // Desplegables (columna B = tipo_dosimetro, columna C = tipo_porta).
            DataValidationHelper helper = sheet.getDataValidationHelper();
            if (!tiposDosimetro.isEmpty()) {
                agregarDesplegable(sheet, helper, 1, ULTIMA_FILA, 1,
                        "opciones!$A$1:$A$" + tiposDosimetro.size());
            }
            if (!tiposPorta.isEmpty()) {
                agregarDesplegable(sheet, helper, 1, ULTIMA_FILA, 2,
                        "opciones!$B$1:$B$" + tiposPorta.size());
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar la plantilla", e);
        }
    }

    // Aplica una lista desplegable a un rango de una columna, referenciando una
    // hoja de opciones (permite listas largas sin el límite de 255 caracteres).
    private void agregarDesplegable(Sheet sheet, DataValidationHelper helper,
                                    int primeraFila, int ultimaFila, int columna, String formula) {
        CellRangeAddressList rango = new CellRangeAddressList(primeraFila, ultimaFila, columna, columna);
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        DataValidation validacion = helper.createValidation(constraint, rango);
        validacion.setSuppressDropDownArrow(true);
        validacion.setShowErrorBox(true);
        validacion.createErrorBox("Valor no válido", "Elige una opción de la lista.");
        sheet.addValidationData(validacion);
    }

    // --- Helpers compartidos ---

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar un archivo Excel");
        }
        if (file.getOriginalFilename() == null ||
                !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Solo se permiten archivos .xlsx");
        }
    }

    /**
     * Parsea y resuelve una fila a entidades. Crea la tarea si no existe.
     * Lanza IllegalArgumentException con un mensaje claro ante datos inválidos.
     */
    private FilaParseada parseFila(Row row, DataFormatter formatter) {
        String numDosimStr = formatter.formatCellValue(row.getCell(COL_NUMDOSIM)).trim();
        if (numDosimStr.isBlank()) {
            throw new IllegalArgumentException("NUMDOSIM vacío");
        }
        int numeroDosimetro;
        try {
            numeroDosimetro = (int) Double.parseDouble(numDosimStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("NUMDOSIM no es un número válido: '" + numDosimStr + "'");
        }

        String tipoDosimNombre = formatter.formatCellValue(row.getCell(COL_TIPO_DOSIMETRO)).trim();
        if (tipoDosimNombre.isBlank()) {
            throw new IllegalArgumentException("TIPO_DOSIMETRO vacío");
        }
        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findByNombre(tipoDosimNombre)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo de dosímetro no encontrado: '" + tipoDosimNombre + "'"));

        boolean esOSL = "OSL".equalsIgnoreCase(tipoDosimetro.getNombre());

        TipoPorta tipoPorta = null;
        String tipoPortaNombre = formatter.formatCellValue(row.getCell(COL_TIPO_PORTA)).trim();
        if (!tipoPortaNombre.isBlank()) {
            tipoPorta = tipoPortaRepository
                    .findByNombreAndTipoDosimetroId(tipoPortaNombre, tipoDosimetro.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tipo de porta '" + tipoPortaNombre + "' no compatible con '" + tipoDosimNombre + "'"));
        }

        Tarea tarea = null;
        Integer numeroBandeja = null;
        Integer slotBandeja = null;
        if (!esOSL) {
            String numRepo = formatter.formatCellValue(row.getCell(COL_NUM_REPOSITORIO)).trim();
            if (!numRepo.isBlank()) {
                tarea = tareaRepository.findByNumeroTarea(numRepo)
                        .orElseGet(() -> {
                            Tarea nueva = new Tarea();
                            nueva.setNumeroTarea(numRepo);
                            nueva.setFechaCreacion(LocalDate.now());
                            return tareaRepository.save(nueva);
                        });
            }

            String bandStr = formatter.formatCellValue(row.getCell(COL_NUM_BANDEJA)).trim();
            if (!bandStr.isBlank()) {
                numeroBandeja = (int) Double.parseDouble(bandStr);
            }

            String slotStr = formatter.formatCellValue(row.getCell(COL_SLOT_BANDEJA)).trim();
            if (!slotStr.isBlank()) {
                slotBandeja = (int) Double.parseDouble(slotStr);
            }
        }

        return new FilaParseada(numeroDosimetro, tipoDosimetro, tipoPorta, tarea, numeroBandeja, slotBandeja);
    }

    private Dosimetro nuevoDosimetro(FilaParseada f) {
        Dosimetro dosimetro = new Dosimetro();
        dosimetro.setNumero(f.numero());
        dosimetro.setTipoDosimetro(f.tipoDosimetro());
        dosimetro.setTipoPorta(f.tipoPorta());
        dosimetro.setTarea(f.tarea());
        dosimetro.setNumeroBandeja(f.numeroBandeja());
        dosimetro.setSlotBandeja(f.slotBandeja());
        dosimetro.setEstado("disponible");
        // Entran al inventario con la fecha de creación de su tarea.
        dosimetro.setFechaCreacion(
                f.tarea() != null ? f.tarea().getFechaCreacion() : LocalDate.now());
        return dosimetro;
    }

    // ¿El dosímetro ya tiene exactamente el mismo armado que la fila del archivo?
    private boolean mismoArmado(Dosimetro d, FilaParseada f) {
        return Objects.equals(idOrNull(d.getTipoDosimetro()), f.tipoDosimetro().getId())
                && Objects.equals(idOrNull(d.getTipoPorta()), idOrNull(f.tipoPorta()))
                && Objects.equals(idOrNull(d.getTarea()), idOrNull(f.tarea()))
                && Objects.equals(d.getNumeroBandeja(), f.numeroBandeja())
                && Objects.equals(d.getSlotBandeja(), f.slotBandeja());
    }

    private Integer idOrNull(TipoDosimetro e) { return e == null ? null : e.getId(); }
    private Integer idOrNull(TipoPorta e) { return e == null ? null : e.getId(); }
    private Integer idOrNull(Tarea e) { return e == null ? null : e.getId(); }

    private boolean esFilaVacia(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = new DataFormatter().formatCellValue(cell).trim();
                if (!val.isEmpty()) return false;
            }
        }
        return true;
    }
}
