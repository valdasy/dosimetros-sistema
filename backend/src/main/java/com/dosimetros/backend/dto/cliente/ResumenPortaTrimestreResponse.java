package com.dosimetros.backend.dto.cliente;

/** Total de dosímetros asignados a un cliente, agrupado por trimestre y tipo de porta. */
public class ResumenPortaTrimestreResponse {

    private final String trimestre;
    private final String tipoPortaNombre;
    private final long cantidad;

    public ResumenPortaTrimestreResponse(String trimestre, String tipoPortaNombre, long cantidad) {
        this.trimestre = trimestre;
        this.tipoPortaNombre = tipoPortaNombre;
        this.cantidad = cantidad;
    }

    public String getTrimestre() { return trimestre; }
    public String getTipoPortaNombre() { return tipoPortaNombre; }
    public long getCantidad() { return cantidad; }
}
