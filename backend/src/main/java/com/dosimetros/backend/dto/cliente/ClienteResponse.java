package com.dosimetros.backend.dto.cliente;

public class ClienteResponse {

    private Integer id;
    private String razonSocial;
    private String nombreCorto;
    private String rut;
    private Boolean activo;
    private Integer ejecutivoId;
    private String ejecutivoNombre;
    // True si el cliente no tiene dosímetros vigentes en estado 'asignado' (HU #16).
    private Boolean pendienteAsignacion;

    public ClienteResponse() {
    }

    public ClienteResponse(Integer id, String razonSocial, String nombreCorto, String rut, Boolean activo,
                           Integer ejecutivoId, String ejecutivoNombre, Boolean pendienteAsignacion) {
        this.id = id;
        this.razonSocial = razonSocial;
        this.nombreCorto = nombreCorto;
        this.rut = rut;
        this.activo = activo;
        this.ejecutivoId = ejecutivoId;
        this.ejecutivoNombre = ejecutivoNombre;
        this.pendienteAsignacion = pendienteAsignacion;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNombreCorto() {
        return nombreCorto;
    }

    public void setNombreCorto(String nombreCorto) {
        this.nombreCorto = nombreCorto;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Integer getEjecutivoId() {
        return ejecutivoId;
    }

    public void setEjecutivoId(Integer ejecutivoId) {
        this.ejecutivoId = ejecutivoId;
    }

    public String getEjecutivoNombre() {
        return ejecutivoNombre;
    }

    public void setEjecutivoNombre(String ejecutivoNombre) {
        this.ejecutivoNombre = ejecutivoNombre;
    }

    public Boolean getPendienteAsignacion() {
        return pendienteAsignacion;
    }

    public void setPendienteAsignacion(Boolean pendienteAsignacion) {
        this.pendienteAsignacion = pendienteAsignacion;
    }
}
