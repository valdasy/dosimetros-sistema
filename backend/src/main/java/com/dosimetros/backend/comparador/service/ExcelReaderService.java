package com.dosimetros.backend.comparador.service;

import com.dosimetros.backend.comparador.model.ColumnMapping;
import com.dosimetros.backend.comparador.model.UserRecord;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelReaderService {

    private final NormalizationService normalizationService;

    // ─────────────────────────────────────────────
    //  ARCHIVO DEL SOFTWARE — estructura fija
    //  Servicio | Usuario | Rut | Area | Reporte | Inicio | Fin | Sede | Dosímetro | Tipo dosímetro | Ubicación
    // ─────────────────────────────────────────────

    public List<UserRecord> readSoftwareFile(MultipartFile file) throws IOException {
        List<UserRecord> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;

            for (Row row : sheet) {
                if (firstRow) { firstRow = false; continue; }
                if (isRowEmpty(row)) continue;

                String servicio = getCellValue(row, 0);
                String usuario  = getCellValue(row, 1);
                String rutRaw   = getCellValue(row, 2);
                String area     = getCellValue(row, 3);
                String sede     = getCellValue(row, 7);
                String tipo     = getCellValue(row, 10); // Ubicación

                String tipoNorm = normalizationService.normalizeName(tipo);
                String rutNorm = normalizationService.normalizeRut(rutRaw);
                String areaNorm = normalizationService.normalizeArea(area);
                String usuarioNorm = normalizationService.normalizeName(usuario);

                records.add(UserRecord.builder()
                        .servicio(servicio)
                        .sede(sede != null ? sede.trim() : "")
                        .area(areaNorm)
                        .usuario(usuarioNorm)
                        .rut(rutNorm)
                        .ubicacion(tipoNorm)
                        .build());
            }
        }
        return records;
    }

    // ─────────────────────────────────────────────
    //  LEER ENCABEZADOS DEL ARCHIVO CLIENTE
    // ─────────────────────────────────────────────

    public Map<String, Object> readClientFileHeaders(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sheets = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                Map<String, Object> sheetInfo = new HashMap<>();
                sheetInfo.put("nombre", workbook.getSheetName(s));
                sheetInfo.put("indice", s);

                List<List<String>> preview = new ArrayList<>();
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount >= 5) break;
                    List<String> rowData = new ArrayList<>();
                    for (int c = 0; c < Math.min(row.getLastCellNum(), 20); c++) {
                        rowData.add(getCellValue(row, c));
                    }
                    preview.add(rowData);
                    rowCount++;
                }
                sheetInfo.put("preview", preview);

                List<String> headers = new ArrayList<>();
                for (Row row : sheet) {
                    boolean hasContent = false;
                    for (int c = 0; c < Math.min(row.getLastCellNum(), 20); c++) {
                        String val = getCellValue(row, c);
                        headers.add(val != null ? val : "(vacío col " + (c+1) + ")");
                        if (val != null && !val.isBlank()) hasContent = true;
                    }
                    if (hasContent) break;
                }
                sheetInfo.put("headers", headers);
                sheets.add(sheetInfo);
            }
        }
        result.put("sheets", sheets);
        return result;
    }

    // ─────────────────────────────────────────────
    //  LEER PLANTILLA FIJA
    //  Columnas: RUT | NOMBRE | APELLIDO_P | APELLIDO_M | AREA | SEDE | TIPO_DOSIMETRO
    // ─────────────────────────────────────────────

    public List<UserRecord> readClientTemplate(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return readClientTemplateFromStream(is);
        }
    }

    public List<UserRecord> readClientTemplateFromBytes(byte[] bytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return readClientTemplateFromStream(is);
        }
    }

    private List<UserRecord> readClientTemplateFromStream(InputStream is) throws IOException {
        List<UserRecord> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheet("LISTADO_CLIENTE");
            if (sheet == null) sheet = workbook.getSheetAt(0);

            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) { firstRow = false; continue; }
                String firstCell = getCellValue(row, 0);
                if (firstCell.startsWith("Ej:") || firstCell.equalsIgnoreCase("RUT")) continue;
                if (isRowEmpty(row)) continue;

                String rutRaw    = getCellValue(row, 0);
                String nombre    = getCellValue(row, 1);
                String apellidoP = getCellValue(row, 2);
                String apellidoM = getCellValue(row, 3);
                String area      = getCellValue(row, 4);
                String sede      = getCellValue(row, 5);
                String tipo      = getCellValue(row, 6);

                String rutNorm = normalizationService.normalizeRut(rutRaw);
                String tipoNorm = normalizationService.normalizeName(tipo);

                String nombreCompleto;
                if (apellidoP.isBlank()) {
                    nombreCompleto = normalizationService.normalizeName(nombre);
                } else {
                    nombreCompleto = normalizationService.buildFullNameFromParts(nombre, apellidoP, apellidoM);
                }

                // Para dosímetros especiales sin RUT, el "usuario" es el tipo
                boolean esEspecial = tipoNorm.contains("CONTROL") || tipoNorm.contains("AMBIENTAL") || tipoNorm.contains("REFERENCIA");
                if (rutNorm.isBlank() && nombreCompleto.isBlank() && !esEspecial) continue;

                records.add(UserRecord.builder()
                        .rutCliente(rutNorm)
                        .nombreCliente(normalizationService.normalizeName(nombre))
                        .apellidoPaternoCliente(normalizationService.normalizeName(apellidoP))
                        .apellidoMaternoCliente(normalizationService.normalizeName(apellidoM))
                        .nombreCompletoCliente(nombreCompleto)
                        .areaCliente(normalizationService.normalizeArea(area))
                        .sedeCliente(sede.trim())
                        .ubicacionCliente(tipoNorm)
                        .build());
            }
        }
        return records;
    }

    // ─────────────────────────────────────────────
    //  DETECCIÓN AUTOMÁTICA DE COLUMNAS
    // ─────────────────────────────────────────────

    public ColumnMapping autoDetectMapping(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return autoDetectMappingFromStream(is);
        }
    }

    public ColumnMapping autoDetectMappingFromBytes(byte[] bytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return autoDetectMappingFromStream(is);
        }
    }

    private ColumnMapping autoDetectMappingFromStream(InputStream is) throws IOException {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setColRut(-1);
        mapping.setColRutSinDv(-1);
        mapping.setColDv(-1);
        mapping.setColNombreCompleto(-1);
        mapping.setColNombre(-1);
        mapping.setColApellidoPaterno(-1);
        mapping.setColApellidoMaterno(-1);
        mapping.setColArea(-1);
        mapping.setColSede(-1);
        mapping.setFilaInicio(1);

        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            mapping.setNombreHoja(workbook.getSheetName(0));

            int headerRowIndex = 0;
            Row headerRow = null;
            for (Row row : sheet) {
                if (!isRowEmpty(row)) { headerRow = row; headerRowIndex = row.getRowNum(); break; }
            }
            if (headerRow == null) return mapping;
            mapping.setFilaInicio(headerRowIndex + 1);

            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String header = getCellValue(headerRow, c).toUpperCase().trim()
                        .replaceAll("[^A-ZÁÉÍÓÚÑ0-9 ]", " ")
                        .replaceAll("\\s+", " ").trim();
                if (header.isBlank()) continue;

                // Importante: el patrón específico "NUMERO RUT"/"DV" (RUT partido en
                // cuerpo + dígito verificador) debe evaluarse ANTES que el genérico
                // "RUT". matchesAny usa contains(), así que "NUMERO RUT" también
                // contiene "RUT": si el genérico se probara primero capturaría la
                // columna del cuerpo como si fuera el RUT completo y el DV terminaría
                // inventándose a partir del último dígito del cuerpo.
                if (matchesAny(header, "NUMERO RUT", "NRO RUT", "CUERPO RUT", "RUT SIN DV")) {
                    mapping.setColRutSinDv(c);
                }
                else if (matchesAny(header, "DV", "D V", "DIGITO", "VERIFICADOR")) {
                    mapping.setColDv(c);
                }
                else if (matchesAny(header, "RUT", "R U T", "RUN", "CEDULA", "DNI", "DOCUMENTO")) {
                    if (mapping.getColRut() == -1) mapping.setColRut(c);
                }
                else if (matchesAny(header, "NOMBRE COMPLETO", "NOMBRE Y APELLIDO", "NOMBRES Y APELLIDOS",
                        "NOMBRE APELLIDO", "APELLIDO NOMBRE", "NOMBRE APELLIDOS")) {
                    mapping.setColNombreCompleto(c);
                }
                else if (matchesAny(header, "NOMBRE", "NOMBRES", "PRIMER NOMBRE")) {
                    if (mapping.getColNombre() == -1) mapping.setColNombre(c);
                }
                else if (header.equals("APELLIDO M") || header.equals("AP MATERNO")
                        || matchesAny(header, "APELLIDO MATERNO", "SEGUNDO APELLIDO", "APELLIDO2", "APE MATERNO")) {
                    mapping.setColApellidoMaterno(c);
                }
                else if (header.equals("APELLIDO P") || header.equals("APELLIDO")
                        || matchesAny(header, "APELLIDO PATERNO", "AP PATERNO", "PRIMER APELLIDO", "APELLIDO1", "APE PATERNO")) {
                    if (mapping.getColApellidoPaterno() == -1) mapping.setColApellidoPaterno(c);
                }
                else if (matchesAny(header, "AREA", "ÁREA", "SECCION", "SECCIÓN", "SERVICIO",
                        "UNIDAD", "DEPARTAMENTO", "DEPTO")) {
                    if (mapping.getColArea() == -1) mapping.setColArea(c);
                }
                else if (matchesAny(header, "SEDE", "SUCURSAL", "ESTABLECIMIENTO", "HOSPITAL",
                        "CLINICA", "CLÍNICA", "CENTRO", "INSTITUCION", "INSTITUCIÓN", "LUGAR")) {
                    if (mapping.getColSede() == -1) mapping.setColSede(c);
                }
            }
        }
        return mapping;
    }

    private boolean matchesAny(String header, String... candidates) {
        for (String candidate : candidates) {
            if (header.equals(candidate) || header.contains(candidate)) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────
    //  LEER ARCHIVO CLIENTE CON MAPEO
    // ─────────────────────────────────────────────

    public List<UserRecord> readClientFileFromBytes(byte[] bytes, String filename, ColumnMapping mapping) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return readClientFileInternal(is, mapping);
        }
    }

    public List<UserRecord> readClientFile(MultipartFile file, ColumnMapping mapping) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return readClientFileInternal(is, mapping);
        }
    }

    private List<UserRecord> readClientFileInternal(InputStream inputStream, ColumnMapping mapping) throws IOException {
        List<UserRecord> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet;
            if (mapping.getNombreHoja() != null && !mapping.getNombreHoja().isBlank()) {
                sheet = workbook.getSheet(mapping.getNombreHoja());
                if (sheet == null) sheet = workbook.getSheetAt(0);
            } else {
                sheet = workbook.getSheetAt(0);
            }

            int rowIndex = 0;
            for (Row row : sheet) {
                if (rowIndex < mapping.getFilaInicio()) { rowIndex++; continue; }
                if (isRowEmpty(row)) { rowIndex++; continue; }

                String rutNorm;
                if (mapping.getColRutSinDv() >= 0 && mapping.getColDv() >= 0) {
                    String cuerpo = getCellValue(row, mapping.getColRutSinDv());
                    String dv = getCellValue(row, mapping.getColDv());
                    rutNorm = normalizationService.normalizeRutFromParts(cuerpo, dv);
                } else {
                    rutNorm = normalizationService.normalizeRut(getCellValue(row, mapping.getColRut()));
                }

                String nombreCompleto;
                String nombre = "", apellidoP = "", apellidoM = "";

                String apellidoPFila = (mapping.getColApellidoPaterno() >= 0)
                        ? getCellValue(row, mapping.getColApellidoPaterno()) : "";
                boolean tieneApellidoSeparado = !apellidoPFila.isBlank();

                if (tieneApellidoSeparado) {
                    nombre    = mapping.getColNombre() >= 0 ? getCellValue(row, mapping.getColNombre()) : "";
                    apellidoP = apellidoPFila;
                    apellidoM = mapping.getColApellidoMaterno() >= 0
                            ? getCellValue(row, mapping.getColApellidoMaterno()) : "";
                    nombreCompleto = normalizationService.buildFullNameFromParts(nombre, apellidoP, apellidoM);
                } else if (mapping.getColNombreCompleto() >= 0) {
                    nombreCompleto = normalizationService.normalizeName(
                            getCellValue(row, mapping.getColNombreCompleto()));
                } else if (mapping.getColNombre() >= 0) {
                    nombreCompleto = normalizationService.normalizeName(
                            getCellValue(row, mapping.getColNombre()));
                } else {
                    nombreCompleto = "";
                }

                String area = mapping.getColArea() >= 0
                        ? normalizationService.normalizeArea(getCellValue(row, mapping.getColArea())) : "";
                String sede = mapping.getColSede() >= 0
                        ? getCellValue(row, mapping.getColSede()) : "";

                if (rutNorm.isBlank() && nombreCompleto.isBlank()) { rowIndex++; continue; }
                if ("REFERENCIA".equals(nombreCompleto)) { rowIndex++; continue; }

                records.add(UserRecord.builder()
                        .rutCliente(rutNorm)
                        .nombreCliente(normalizationService.normalizeName(nombre))
                        .apellidoPaternoCliente(normalizationService.normalizeName(apellidoP))
                        .apellidoMaternoCliente(normalizationService.normalizeName(apellidoM))
                        .nombreCompletoCliente(nombreCompleto)
                        .areaCliente(area)
                        .sedeCliente(sede != null ? sede.trim() : "")
                        .build());

                rowIndex++;
            }
        }
        return records;
    }

    // ─── UTILIDADES ───

    private String getCellValue(Row row, int colIndex) {
        if (colIndex < 0) return "";
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield String.valueOf((long) val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            String val = getCellValue(row, c);
            if (val != null && !val.isBlank()) return false;
        }
        return true;
    }
}
