package com.dosimetros.backend.informeisp.service;

import com.dosimetros.backend.informeisp.model.FilaInforme;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee el "Informe de Dosis" crudo (hoja Informe_dosis, encabezados en la primera
 * fila). Localiza las columnas por nombre para tolerar cambios de orden.
 */
@Service
public class InformeReaderService {

    public List<FilaInforme> leer(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IllegalArgumentException("El archivo no tiene encabezados.");
            }
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header) {
                String name = fmt.formatCellValue(c).trim().toLowerCase();
                if (!name.isEmpty()) {
                    col.putIfAbsent(name, c.getColumnIndex());
                }
            }
            requerir(col, "rut");
            requerir(col, "dosimetro");
            requerir(col, "ubicacion");

            List<FilaInforme> filas = new ArrayList<>();
            int firstData = header.getRowNum() + 1;
            for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                FilaInforme f = new FilaInforme();
                f.filaExcel = r + 1; // 1-based para el usuario
                f.cliente = val(fmt, row, col, "cliente");
                f.documentoCliente = val(fmt, row, col, "documentocliente");
                f.rut = val(fmt, row, col, "rut");
                f.usuario = val(fmt, row, col, "usuario");
                f.area = val(fmt, row, col, "area"); // informativo, opcional
                f.genero = val(fmt, row, col, "genero");
                f.tipoDosimetro = val(fmt, row, col, "tipodosimetro");
                f.dosimetro = val(fmt, row, col, "dosimetro");
                f.fechaInicio = val(fmt, row, col, "fechainicio");
                f.fechaFin = val(fmt, row, col, "fechafin");
                f.dosisProfundidad = val(fmt, row, col, "dosis profundidad");
                f.dosisPiel = val(fmt, row, col, "dosis piel");
                f.dosisCristalino = val(fmt, row, col, "dosis cristalino");
                f.ubicacion = val(fmt, row, col, "ubicacion");
                f.periodicidad = val(fmt, row, col, "periodicidad");
                f.tecnologia = val(fmt, row, col, "tecnologia"); // opcional

                if (esFilaVacia(f)) continue;
                filas.add(f);
            }
            return filas;
        }
    }

    private void requerir(Map<String, Integer> col, String nombre) {
        if (!col.containsKey(nombre)) {
            throw new IllegalArgumentException(
                    "El informe no tiene la columna requerida: '" + nombre + "'.");
        }
    }

    private String val(DataFormatter fmt, Row row, Map<String, Integer> col, String nombre) {
        Integer idx = col.get(nombre);
        if (idx == null) return "";
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private boolean esFilaVacia(FilaInforme f) {
        return IspMappings.vacio(f.rut) && IspMappings.vacio(f.dosimetro)
                && IspMappings.vacio(f.usuario) && IspMappings.vacio(f.cliente);
    }
}
