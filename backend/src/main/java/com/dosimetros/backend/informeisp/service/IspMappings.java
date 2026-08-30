package com.dosimetros.backend.informeisp.service;

import java.util.Set;

/**
 * Mapeos y constantes del dominio ISP. Ver docs/INFORME_ISP.md.
 */
public final class IspMappings {

    private IspMappings() {
    }

    // Tipos de dosímetro que NO son personas (se filtran de la salida).
    public static final Set<String> TIPOS_NO_PERSONA = Set.of(
            "AMBIENTAL", "DE CONTROL", "DE REFERENCIA");

    // Tipos de dosímetro de persona.
    public static final Set<String> TIPOS_PERSONA = Set.of(
            "CUERPO COMPLETO/ PERSONAL", "CUERPO COMPLETO/PERSONAL", "EXTREMIDAD");

    /** Normalización simple y consistente para claves (RUT, textos). */
    public static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    public static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** ¿El valor de dosis es un dato real (no vacío ni "-")? */
    public static boolean sinDato(String v) {
        String n = norm(v);
        return n.isEmpty() || n.equals("-");
    }

    /**
     * Magnitud (HP10 | HP0.07 | HP3) según la Ubicacion, o null si no mapea.
     */
    public static String magnitudDeUbicacion(String ubicacion) {
        String u = norm(ubicacion);
        switch (u) {
            case "PERSONAL":
            case "ABDOMINAL":
                return "HP10";
            case "ANILLO":
            case "DEDO":
            case "PULSERA":
            case "MUÑECA":
            case "MUNECA":
            case "BRAZO":
            case "ANILLO/ PULSERA":
            case "ANILLO/PULSERA":
                return "HP0.07";
            case "TIROIDEO":
            case "CRISTALINO":
                return "HP3";
            default:
                return null;
        }
    }

    /** Código de Localización del dosímetro (clas. 4) según la Ubicacion, o null. */
    public static Integer localizacionDeUbicacion(String ubicacion) {
        String u = norm(ubicacion);
        switch (u) {
            case "PERSONAL":
            case "ABDOMINAL":
                return 1; // Tórax
            case "ANILLO":
            case "DEDO":
            case "ANILLO/ PULSERA":
            case "ANILLO/PULSERA":
                return 2; // Extremidades (dedo)
            case "PULSERA":
            case "MUÑECA":
            case "MUNECA":
            case "BRAZO":
                return 3; // Extremidades (brazo, muñeca)
            case "TIROIDEO":
            case "CRISTALINO":
                return 4; // Cabeza (cristalino)
            default:
                return null;
        }
    }

    /** La columna de dosis del informe que corresponde a una magnitud. */
    public static String columnaDosisDeMagnitud(String magnitud, FilaDosisInput in) {
        switch (magnitud) {
            case "HP10":
                return in.profundidad;
            case "HP0.07":
                return in.piel;
            case "HP3":
                return in.cristalino;
            default:
                return null;
        }
    }

    /** Tupla mínima para elegir la columna de dosis. */
    public static final class FilaDosisInput {
        public final String profundidad;
        public final String piel;
        public final String cristalino;

        public FilaDosisInput(String profundidad, String piel, String cristalino) {
            this.profundidad = profundidad;
            this.piel = piel;
            this.cristalino = cristalino;
        }
    }

    /** Periodicidad del informe -> periodicidad del catálogo de códigos. */
    public static String periodicidadCodigo(String periodicidad) {
        String p = norm(periodicidad);
        if (p.equals("BIMESTRAL") || p.equals("BIMENSUAL")) return "BIMENSUAL";
        if (p.equals("MENSUAL")) return "MENSUAL";
        if (p.equals("TRIMESTRAL")) return "TRIMESTRAL";
        return p;
    }

    /** Género (Masculino/Femenino) -> Sexo ISP (M/F). */
    public static String sexoIsp(String genero) {
        String g = norm(genero);
        if (g.startsWith("M")) return "M";
        if (g.startsWith("F")) return "F";
        return "";
    }

    /**
     * Resultado de interpretar un valor de dosis del informe.
     * dosis = valor numérico (null si va vacío); observa = sigla ISP o null.
     */
    public static final class DosisIsp {
        public final Double dosis;
        public final String observa;

        public DosisIsp(Double dosis, String observa) {
            this.dosis = dosis;
            this.observa = observa;
        }
    }

    /**
     * Interpreta un valor de dosis (numérico o sigla) al formato del ISP:
     *   numérico  -> (valor 2 decimales, observa vacío)
     *   MNR       -> (0.00, "<LD")
     *   DSU       -> (0.00, "NU")
     *   DND/DD/DE/NR -> (0.00, "NR")
     */
    public static DosisIsp interpretarDosis(String valor) {
        String v = norm(valor);
        if (v.isEmpty() || v.equals("-")) {
            return new DosisIsp(0.0, "NR"); // sin dato en la magnitud esperada -> no retornado
        }
        // ¿numérico? (acepta coma decimal)
        String num = v.replace(",", ".");
        try {
            double d = Double.parseDouble(num);
            double redondeado = Math.round(d * 100.0) / 100.0;
            return new DosisIsp(redondeado, null);
        } catch (NumberFormatException ignored) {
            // es una sigla
        }
        switch (v) {
            case "MNR":
                return new DosisIsp(0.0, "<LD");
            case "DSU":
                return new DosisIsp(0.0, "NU");
            case "DND":
            case "DD":
            case "DE":
            case "NR":
                return new DosisIsp(0.0, "NR");
            default:
                return new DosisIsp(0.0, "NR"); // sigla desconocida -> conservador
        }
    }
}
