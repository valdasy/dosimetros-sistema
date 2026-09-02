package com.dosimetros.backend.informeisp;

import com.dosimetros.backend.informeisp.model.FilaDosis;
import com.dosimetros.backend.informeisp.model.ResultadoProceso;
import com.dosimetros.backend.informeisp.service.IspExcelWriterService;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class IspExcelWriterServiceTest {

    private final IspExcelWriterService writer = new IspExcelWriterService();

    @Test
    void dosisIncluyeColumnasDeAyudaClienteYArea() throws Exception {
        ResultadoProceso res = new ResultadoProceso();
        res.empresa = "Dosimet";
        FilaDosis d = new FilaDosis();
        d.run = "11111111-1";
        d.cliente = "HOSPITAL X";
        d.area = "IMAGENOLOGÍA";
        d.rutEntidad = "76000000-0";
        d.codServ = 3;
        d.dosis = 0.10;
        res.dosis.add(d);

        byte[] bytes = writer.escribir(res);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet dosis = wb.getSheet("DOSIS");
            assertNotNull(dosis, "debe existir la hoja DOSIS");

            DataFormatter fmt = new DataFormatter();
            Row header = dosis.getRow(5);
            assertEquals("CLIENTE", fmt.formatCellValue(header.getCell(23)));
            assertEquals("AREA", fmt.formatCellValue(header.getCell(24)));

            // La primera fila de datos de DOSIS es la fila 7 (índice 0-based).
            Row fila = dosis.getRow(7);
            assertEquals("HOSPITAL X", fmt.formatCellValue(fila.getCell(23)));
            assertEquals("IMAGENOLOGÍA", fmt.formatCellValue(fila.getCell(24)));

            // No debe romper el formato oficial: RUN y COD SERV siguen en su lugar.
            assertEquals("11111111-1", fmt.formatCellValue(fila.getCell(2)));
            assertEquals("3", fmt.formatCellValue(fila.getCell(4)));
        }
    }

    @Test
    void codigoSugeridoSePintaEnDosis() throws Exception {
        ResultadoProceso res = new ResultadoProceso();
        res.empresa = "Dosimet";
        FilaDosis d = new FilaDosis();
        d.run = "22222222-2";
        d.codPrac = 4;
        d.codCargo = 3;
        d.pracSugerido = true;   // sugerido -> celda pintada
        d.cargoSugerido = false; // confirmado -> sin pintar
        d.dosis = 0.0;
        res.dosis.add(d);

        byte[] bytes = writer.escribir(res);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet dosis = wb.getSheet("DOSIS");
            Row fila = dosis.getRow(7);
            short amarillo = IndexedColors.LIGHT_YELLOW.getIndex();
            assertEquals(amarillo, fila.getCell(8).getCellStyle().getFillForegroundColor(),
                    "COD PRAC sugerido debe ir pintado");
            assertNotEquals(amarillo, fila.getCell(10).getCellStyle().getFillForegroundColor(),
                    "COD CARGO confirmado no debe ir pintado");
        }
    }
}
