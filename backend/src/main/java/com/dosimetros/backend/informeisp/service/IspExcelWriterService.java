package com.dosimetros.backend.informeisp.service;

import com.dosimetros.backend.informeisp.model.FilaDosis;
import com.dosimetros.backend.informeisp.model.Inconsistencia;
import com.dosimetros.backend.informeisp.model.PersonaToes;
import com.dosimetros.backend.informeisp.model.ResultadoProceso;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Escribe el libro del ISP replicando el formato oficial: hojas TOES y DOSIS con
 * encabezados en filas intermedias y columnas separadoras B1..B9, más una hoja
 * REVISION con las inconsistencias. Ver docs/INFORME_ISP.md.
 */
@Service
public class IspExcelWriterService {

    public byte[] escribir(ResultadoProceso res) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);

            CellStyle num2 = wb.createCellStyle();
            num2.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            String proveedor = res.empresa == null ? "" : res.empresa.toUpperCase();

            escribirToes(wb, res, proveedor, bold);
            escribirDosis(wb, res, proveedor, bold, num2);
            escribirRevision(wb, res, bold);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void escribirToes(Workbook wb, ResultadoProceso res, String proveedor, CellStyle bold) {
        Sheet s = wb.createSheet("TOES");
        text(s, 1, 2, "NOMBRE DEL PROVEEDOR - " + proveedor, bold);
        text(s, 2, 2, "REGISTRO DE TRABAJADORES OCUPACIONALMENTE EXPUESTOS", bold);
        text(s, 5, 2, "DATOS QUE SE EXPORTAN", bold);

        Row h = s.createRow(6);
        setBold(h, 0, "ERRORES", bold);
        setBold(h, 1, "EXPORTADO", bold);
        setBold(h, 2, "REGISTRO ÚNICO NACIONAL RUN", bold);
        setBold(h, 3, "B1", bold);
        setBold(h, 4, "Nombre y Apellido del Trabajador", bold);
        setBold(h, 5, "B2", bold);
        setBold(h, 6, "Sexo (F/M)", bold);
        setBold(h, 7, "B3", bold);
        setBold(h, 8, "Fecha de Nacimiento", bold);
        setBold(h, 9, "B4", bold);
        setBold(h, 10, "PAÍS", bold);

        Row t = s.createRow(7);
        t.createCell(2).setCellValue("C(15)");
        t.createCell(4).setCellValue("C(80)");
        t.createCell(6).setCellValue("C(1)");
        t.createCell(8).setCellValue("D(10) (DD/MM/AAAA)");
        t.createCell(10).setCellValue("C(2)");

        int r = 8;
        for (PersonaToes p : res.toes) {
            Row row = s.createRow(r++);
            row.createCell(2).setCellValue(nz(p.run));
            row.createCell(4).setCellValue(nz(p.nombre));
            row.createCell(6).setCellValue(nz(p.sexo));
            if (p.fechaNacimiento != null) row.createCell(8).setCellValue(p.fechaNacimiento);
            if (p.pais != null) row.createCell(10).setCellValue(p.pais);
        }
    }

    private void escribirDosis(Workbook wb, ResultadoProceso res, String proveedor,
                               CellStyle bold, CellStyle num2) {
        Sheet s = wb.createSheet("DOSIS");
        text(s, 1, 2, "NOMBRE DEL PROVEEDOR - " + proveedor, bold);
        text(s, 2, 2, "REGISTRO DOSIMÉTRICO", bold);
        text(s, 4, 12, "PERIODO MONITOREO", bold);
        text(s, 4, 22, "OBSERVACIONES", bold);
        // Bloque de AYUDA (no forma parte del formato oficial del ISP): facilita
        // completar a mano los COD PRAC / COD CARGO que quedaron en blanco.
        text(s, 4, 23, "AYUDA (no oficial · borrar antes de enviar al ISP)", bold);

        Row h = s.createRow(5);
        setBold(h, 0, "ERRORES", bold);
        setBold(h, 1, "EXPORTADO", bold);
        setBold(h, 2, "REGISTRO ÚNICO NACIONAL RUN", bold);
        setBold(h, 3, "B1", bold);
        setBold(h, 4, "COD SERV", bold);
        setBold(h, 5, "B2", bold);
        setBold(h, 6, "ROL ÚNICO TRIBUTARIO RUT", bold);
        setBold(h, 7, "B3", bold);
        setBold(h, 8, "COD PRAC", bold);
        setBold(h, 9, "B4", bold);
        setBold(h, 10, "COD CARGO", bold);
        setBold(h, 11, "B5", bold);
        setBold(h, 12, "FEC INIC  MONIT", bold);
        setBold(h, 13, "B6", bold);
        setBold(h, 14, "FEC FIN MONIT", bold);
        setBold(h, 15, "B7", bold);
        setBold(h, 16, "Dosis [mSv]", bold);
        setBold(h, 17, "B8", bold);
        setBold(h, 18, "CANT", bold);
        setBold(h, 19, "B9", bold);
        setBold(h, 20, "OBSERVA", bold);
        setBold(h, 23, "CLIENTE", bold);   // ayuda (no oficial)
        setBold(h, 24, "AREA", bold);      // ayuda (no oficial)

        Row t = s.createRow(6);
        t.createCell(2).setCellValue("C(15)");
        t.createCell(4).setCellValue("CLASIFICADOR");
        t.createCell(6).setCellValue("C(15)");
        t.createCell(8).setCellValue("CLASIFICADOR");
        t.createCell(10).setCellValue("CLASIFICADOR");
        t.createCell(12).setCellValue("D(10) (DD/MM/AAAA)");
        t.createCell(14).setCellValue("D(10) (DD/MM/AAAA)");
        t.createCell(16).setCellValue("FLOAT");
        t.createCell(18).setCellValue("ENTERO");

        // Estilo para códigos SUGERIDOS por compañeros (no confirmados por maestra):
        // fondo ámbar para que salten a la vista y se verifiquen antes de enviar.
        CellStyle sugerido = wb.createCellStyle();
        sugerido.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        sugerido.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int r = 7;
        for (FilaDosis d : res.dosis) {
            Row row = s.createRow(r++);
            row.createCell(2).setCellValue(nz(d.run));
            if (d.codServ != null) row.createCell(4).setCellValue(d.codServ);
            row.createCell(6).setCellValue(nz(d.rutEntidad));
            if (d.codPrac != null) {
                Cell c = row.createCell(8);
                c.setCellValue(d.codPrac);
                if (d.pracSugerido) c.setCellStyle(sugerido);
            }
            if (d.codCargo != null) {
                Cell c = row.createCell(10);
                c.setCellValue(d.codCargo);
                if (d.cargoSugerido) c.setCellStyle(sugerido);
            }
            row.createCell(12).setCellValue(nz(d.fechaInicio));
            row.createCell(14).setCellValue(nz(d.fechaFin));
            Cell dosisCell = row.createCell(16);
            dosisCell.setCellValue(d.dosis == null ? 0.0 : d.dosis);
            dosisCell.setCellStyle(num2);
            row.createCell(18).setCellValue(1);
            if (d.observa != null) row.createCell(20).setCellValue(d.observa);
            // Ayuda (no oficial): cliente y área para ubicar los códigos manuales.
            row.createCell(23).setCellValue(nz(d.cliente));
            row.createCell(24).setCellValue(nz(d.area));
        }
        s.autoSizeColumn(23);
        s.autoSizeColumn(24);
    }

    private void escribirRevision(Workbook wb, ResultadoProceso res, CellStyle bold) {
        Sheet s = wb.createSheet("REVISION");
        Row h = s.createRow(0);
        setBold(h, 0, "Fila", bold);
        setBold(h, 1, "RUT", bold);
        setBold(h, 2, "Usuario", bold);
        setBold(h, 3, "Cliente", bold);
        setBold(h, 4, "Tipo", bold);
        setBold(h, 5, "Detalle", bold);

        int r = 1;
        for (Inconsistencia inc : res.inconsistencias) {
            Row row = s.createRow(r++);
            row.createCell(0).setCellValue(inc.filaExcel);
            row.createCell(1).setCellValue(nz(inc.rut));
            row.createCell(2).setCellValue(nz(inc.usuario));
            row.createCell(3).setCellValue(nz(inc.cliente));
            row.createCell(4).setCellValue(nz(inc.tipo));
            row.createCell(5).setCellValue(nz(inc.detalle));
        }
        for (int c = 0; c <= 5; c++) s.autoSizeColumn(c);
    }

    private void text(Sheet s, int rowIdx, int colIdx, String value, CellStyle style) {
        Row row = s.getRow(rowIdx);
        if (row == null) row = s.createRow(rowIdx);
        Cell c = row.createCell(colIdx);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void setBold(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
