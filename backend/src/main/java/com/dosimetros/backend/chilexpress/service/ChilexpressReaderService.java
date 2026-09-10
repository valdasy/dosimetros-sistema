package com.dosimetros.backend.chilexpress.service;

import com.dosimetros.backend.chilexpress.model.ArchivoChilexpress;
import com.dosimetros.backend.chilexpress.model.FilaChilexpress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el "export" de Chilexpress. Aunque tiene extensión .xls, en realidad es
 * un documento HTML (una tabla). Este lector lo parsea sin dependencias
 * externas: extrae filas y celdas, ubica los encabezados por nombre (tolerante
 * a acentos/mayúsculas y a cambios de orden) y arma las Órdenes de Transporte.
 */
@Service
public class ChilexpressReaderService {

    private static final Pattern TR = Pattern.compile("<tr[^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TD = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    // Encabezado normalizado -> clave de campo.
    private static final Map<String, String> COLS = new HashMap<>();
    static {
        COLS.put("nro ot", "nroOt");
        COLS.put("ot padre", "otPadre");
        COLS.put("nro referencia", "nroReferencia");
        COLS.put("tiempo de entrega o servicio", "servicio");
        COLS.put("valor declarado producto", "valorDeclarado");
        COLS.put("estado", "estado");
        COLS.put("nombre destinatario", "nombreDestinatario");
        COLS.put("destino", "destino");
        COLS.put("direccion del destinatario", "direccion");
        COLS.put("oficina origen", "oficinaOrigen");
        COLS.put("oficina destino", "oficinaDestino");
        COLS.put("tipo admision", "tipoAdmision");
        COLS.put("tipo entrega", "tipoEntrega");
        COLS.put("receptor", "receptor");
        COLS.put("rut receptor", "rutReceptor");
        COLS.put("fecha primer intento entrega", "fechaPrimerIntento");
        COLS.put("fecha entrega", "fechaEntrega");
        COLS.put("hora entrega", "horaEntrega");
        COLS.put("certificado de entrega", "certificadoEntrega");
    }

    public ArchivoChilexpress leer(MultipartFile file) throws IOException {
        String texto = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<List<String>> filas = new ArrayList<>();
        List<List<String>> filasHref = new ArrayList<>();
        Matcher mTr = TR.matcher(texto);
        while (mTr.find()) {
            String tr = mTr.group(1);
            List<String> celdas = new ArrayList<>();
            List<String> hrefs = new ArrayList<>();
            Matcher mTd = TD.matcher(tr);
            while (mTd.find()) {
                String cel = mTd.group(1);
                Matcher mh = HREF.matcher(cel);
                hrefs.add(mh.find() ? mh.group(1).trim() : "");
                celdas.add(limpiar(cel));
            }
            if (celdas.stream().anyMatch(s -> !s.isEmpty()) || !hrefs.isEmpty()) {
                filas.add(celdas);
                filasHref.add(hrefs);
            }
        }

        ArchivoChilexpress out = new ArchivoChilexpress();

        // Localiza la fila de encabezados (la que tiene "Nro. OT").
        int hdrIdx = -1;
        for (int i = 0; i < filas.size(); i++) {
            for (String c : filas.get(i)) {
                if (norm(c).equals("nro ot")) { hdrIdx = i; break; }
            }
            if (hdrIdx >= 0) break;
        }
        if (hdrIdx < 0) {
            throw new IllegalArgumentException(
                    "El archivo no parece un export de Chilexpress (no se encontró la columna 'Nro. OT').");
        }

        // Periodo (Fecha Desde / Fecha Hasta) desde los metadatos previos.
        List<String> meta = new ArrayList<>();
        for (int i = 0; i < hdrIdx; i++) meta.addAll(filas.get(i));
        out.periodoDesde = valorTrasEtiqueta(meta, "fecha desde");
        out.periodoHasta = valorTrasEtiqueta(meta, "fecha hasta");

        // Mapa columna -> campo.
        List<String> hdr = filas.get(hdrIdx);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < hdr.size(); i++) {
            String campo = COLS.get(norm(hdr.get(i)));
            if (campo != null) idx.putIfAbsent(campo, i);
        }

        for (int r = hdrIdx + 1; r < filas.size(); r++) {
            List<String> row = filas.get(r);
            List<String> hrefs = filasHref.get(r);
            String nroOt = get(row, idx.get("nroOt"));
            if (nroOt.isEmpty()) continue; // salta totales/vacías

            FilaChilexpress f = new FilaChilexpress();
            f.nroOt = nroOt;
            f.otPadre = get(row, idx.get("otPadre"));
            f.nroReferencia = get(row, idx.get("nroReferencia"));
            f.nombreDestinatario = get(row, idx.get("nombreDestinatario"));
            f.destino = get(row, idx.get("destino"));
            f.direccion = get(row, idx.get("direccion"));
            f.servicio = get(row, idx.get("servicio"));
            f.valorDeclarado = entero(get(row, idx.get("valorDeclarado")));
            f.oficinaOrigen = get(row, idx.get("oficinaOrigen"));
            f.oficinaDestino = get(row, idx.get("oficinaDestino"));
            f.tipoAdmision = get(row, idx.get("tipoAdmision"));
            f.tipoEntrega = get(row, idx.get("tipoEntrega"));
            f.estado = get(row, idx.get("estado"));
            f.receptor = get(row, idx.get("receptor"));
            f.rutReceptor = get(row, idx.get("rutReceptor"));
            f.fechaPrimerIntento = fecha(get(row, idx.get("fechaPrimerIntento")));
            f.fechaEntrega = fecha(get(row, idx.get("fechaEntrega")));
            f.horaEntrega = get(row, idx.get("horaEntrega"));
            // El certificado suele venir como enlace; toma el href si existe.
            Integer ci = idx.get("certificadoEntrega");
            String cert = get(row, ci);
            if (ci != null && ci < hrefs.size() && !hrefs.get(ci).isEmpty()) cert = hrefs.get(ci);
            f.certificadoEntrega = cert;

            out.filas.add(f);
        }
        return out;
    }

    private static String get(List<String> row, Integer i) {
        if (i == null || i < 0 || i >= row.size()) return "";
        return row.get(i) == null ? "" : row.get(i).trim();
    }

    private static String limpiar(String celda) {
        String s = TAG.matcher(celda).replaceAll(" ");
        s = desescapar(s);
        return s.replaceAll("\\s+", " ").trim();
    }

    private static final Pattern ENTITY_NUM = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private static String desescapar(String s) {
        s = s.replace("&nbsp;", " ");
        // Entidades numéricas: &#233; -> é, &#8211; -> –, &#xE9; -> é, etc.
        Matcher m = ENTITY_NUM.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int code = m.group(1).isEmpty()
                        ? Integer.parseInt(m.group(2))
                        : Integer.parseInt(m.group(2), 16);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        // Entidades con nombre más comunes (por si aparecen).
        return sb.toString().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&aacute;", "á").replace("&eacute;", "é")
                .replace("&iacute;", "í").replace("&oacute;", "ó").replace("&uacute;", "ú")
                .replace("&ntilde;", "ñ");
    }

    /** Normaliza un texto: sin acentos, minúsculas, solo alfanumérico y espacios. */
    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        return n.replaceAll("\\s+", " ");
    }

    private static Integer entero(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9-]", "");
        if (d.isEmpty()) return null;
        try { return Integer.parseInt(d); } catch (NumberFormatException e) { return null; }
    }

    private static final DateTimeFormatter[] FMTS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    private static LocalDate fecha(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty() || v.equals("-")) return null;
        for (DateTimeFormatter f : FMTS) {
            try { return LocalDate.parse(v, f); } catch (Exception ignored) { }
        }
        return null;
    }

    /** Busca una etiqueta (ej. "fecha desde") entre las celdas y devuelve el siguiente valor-fecha. */
    private static LocalDate valorTrasEtiqueta(List<String> celdas, String etiqueta) {
        for (int i = 0; i < celdas.size(); i++) {
            if (norm(celdas.get(i)).startsWith(etiqueta)) {
                for (int j = i + 1; j < celdas.size(); j++) {
                    LocalDate d = fecha(celdas.get(j));
                    if (d != null) return d;
                }
            }
        }
        return null;
    }
}
