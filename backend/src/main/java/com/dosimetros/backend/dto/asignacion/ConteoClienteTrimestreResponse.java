package com.dosimetros.backend.dto.asignacion;

/**
 * Cantidad de asignaciones de un cliente en un trimestre (#18: pendiente de
 * asignación / comparación por trimestres). El frontend arma la matriz
 * cliente × trimestre; donde no hay conteo, el cliente está pendiente.
 */
public class ConteoClienteTrimestreResponse {

    private Integer clienteId;
    private String razonSocial;
    private String nombreCorto;
    private String trimestre;
    private long cantidad;

    public ConteoClienteTrimestreResponse() {
    }

    public ConteoClienteTrimestreResponse(Integer clienteId, String razonSocial, String nombreCorto,
                                          String trimestre, long cantidad) {
        this.clienteId = clienteId;
        this.razonSocial = razonSocial;
        this.nombreCorto = nombreCorto;
        this.trimestre = trimestre;
        this.cantidad = cantidad;
    }

    public String getNombreCorto() {
        return nombreCorto;
    }

    public void setNombreCorto(String nombreCorto) {
        this.nombreCorto = nombreCorto;
    }

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(String trimestre) {
        this.trimestre = trimestre;
    }

    public long getCantidad() {
        return cantidad;
    }

    public void setCantidad(long cantidad) {
        this.cantidad = cantidad;
    }
}
