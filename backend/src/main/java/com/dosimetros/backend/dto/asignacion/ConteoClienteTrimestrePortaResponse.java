package com.dosimetros.backend.dto.asignacion;

/**
 * Conteo de asignaciones por cliente, trimestre y tipo de porta. Sirve para
 * desglosar por porta los dosímetros pendientes de asignación (lo que un cliente
 * tenía en el trimestre base) en el módulo "Pendiente de asignación".
 */
public class ConteoClienteTrimestrePortaResponse {

    private Integer clienteId;
    private String trimestre;
    private Integer tipoPortaId;
    private String tipoPortaNombre;
    private long cantidad;

    public ConteoClienteTrimestrePortaResponse() {
    }

    public ConteoClienteTrimestrePortaResponse(Integer clienteId, String trimestre,
                                               Integer tipoPortaId, String tipoPortaNombre, long cantidad) {
        this.clienteId = clienteId;
        this.trimestre = trimestre;
        this.tipoPortaId = tipoPortaId;
        this.tipoPortaNombre = tipoPortaNombre;
        this.cantidad = cantidad;
    }

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public String getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(String trimestre) {
        this.trimestre = trimestre;
    }

    public Integer getTipoPortaId() {
        return tipoPortaId;
    }

    public void setTipoPortaId(Integer tipoPortaId) {
        this.tipoPortaId = tipoPortaId;
    }

    public String getTipoPortaNombre() {
        return tipoPortaNombre;
    }

    public void setTipoPortaNombre(String tipoPortaNombre) {
        this.tipoPortaNombre = tipoPortaNombre;
    }

    public long getCantidad() {
        return cantidad;
    }

    public void setCantidad(long cantidad) {
        this.cantidad = cantidad;
    }
}
