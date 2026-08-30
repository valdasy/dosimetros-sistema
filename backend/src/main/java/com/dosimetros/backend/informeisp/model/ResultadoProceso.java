package com.dosimetros.backend.informeisp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de procesar un Informe de Dosis: filas para TOES y DOSIS,
 * inconsistencias detectadas y un resumen de conteos.
 */
public class ResultadoProceso {

    public String empresa;
    public List<PersonaToes> toes = new ArrayList<>();
    public List<FilaDosis> dosis = new ArrayList<>();
    public List<Inconsistencia> inconsistencias = new ArrayList<>();

    public int filasTotales;
    public int filasEliminadas;
    public int filasProcesadas;
    public int personasUnicas;

    public Map<String, Integer> resumen() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("filasTotales", filasTotales);
        m.put("filasEliminadas", filasEliminadas);
        m.put("filasProcesadas", filasProcesadas);
        m.put("personasUnicas", personasUnicas);
        m.put("inconsistencias", inconsistencias.size());
        return m;
    }
}
