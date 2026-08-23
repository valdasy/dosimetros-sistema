package com.dosimetros.backend.comparador.service;

import com.dosimetros.backend.comparador.model.ColumnMapping;
import com.dosimetros.backend.comparador.model.UserRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre el hallazgo crítico #2: cuando el archivo del cliente trae el RUT
 * partido en dos columnas ("NUMERO RUT" + "DV"), la auto-detección debe
 * mapear ambas y reconstruir el RUT completo, en vez de tratar la columna del
 * cuerpo como un RUT completo e inventar el dígito verificador.
 */
class ExcelReaderServiceTest {

    private final ExcelReaderService service = new ExcelReaderService(new NormalizationService());

    private byte[] archivoConRutPartido() throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Hoja1");
            crearFila(sheet, 0, "NUMERO RUT", "DV", "NOMBRE", "APELLIDO PATERNO");
            crearFila(sheet, 1, "12345678", "9", "JUAN", "PEREZ");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void crearFila(Sheet sheet, int idx, String... valores) {
        Row row = sheet.createRow(idx);
        for (int c = 0; c < valores.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(valores[c]);
        }
    }

    @Test
    void autoDetecta_columnas_cuerpo_y_dv_separadas() throws Exception {
        ColumnMapping mapping = service.autoDetectMappingFromBytes(archivoConRutPartido());

        // "NUMERO RUT" debe caer en colRutSinDv (no en colRut) y "DV" en colDv.
        assertEquals(0, mapping.getColRutSinDv(), "la columna del cuerpo debe mapearse como colRutSinDv");
        assertEquals(1, mapping.getColDv(), "la columna DV debe mapearse como colDv");
        assertEquals(-1, mapping.getColRut(), "no debe existir una columna de RUT completo");
    }

    @Test
    void reconstruye_el_rut_completo_sin_inventar_el_dv() throws Exception {
        byte[] bytes = archivoConRutPartido();
        ColumnMapping mapping = service.autoDetectMappingFromBytes(bytes);

        List<UserRecord> registros = service.readClientFileFromBytes(bytes, "clientes.xlsx", mapping);

        assertEquals(1, registros.size());
        // Antes del fix, el cuerpo caía a normalizeRut() y quedaba "1234567-8".
        assertEquals("12345678-9", registros.get(0).getRutCliente());
        assertTrue(registros.get(0).getRutCliente().startsWith("12345678-"),
                "el cuerpo del RUT no debe perder su último dígito");
    }
}
