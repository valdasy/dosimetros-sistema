package com.dosimetros.backend.informeisp.dto;

import com.dosimetros.backend.informeisp.model.Inconsistencia;

import java.util.List;
import java.util.Map;

/** Vista previa del procesamiento de un informe (sin generar el Excel). */
public class AnalisisResponse {

    private final String empresa;
    private final Map<String, Integer> resumen;
    private final List<Inconsistencia> inconsistencias;

    public AnalisisResponse(String empresa, Map<String, Integer> resumen,
                            List<Inconsistencia> inconsistencias) {
        this.empresa = empresa;
        this.resumen = resumen;
        this.inconsistencias = inconsistencias;
    }

    public String getEmpresa() {
        return empresa;
    }

    public Map<String, Integer> getResumen() {
        return resumen;
    }

    public List<Inconsistencia> getInconsistencias() {
        return inconsistencias;
    }
}
