package com.dosimetros.backend.dto.asignacion;

import java.time.LocalDate;

public class AsignacionResponse {

    private Integer id;
    private Integer dosimetroId;
    private Integer numeroDosimetro;
    private Integer clienteId;
    private String clienteNombre;
    private String clienteNombreCorto;
    private Integer ejecutivoId;
    private String ejecutivoNombre;
    private Integer empresaId;
    private String empresaNombre;
    private Integer tipoPortaId;
    private String tipoPortaNombre;
    private Integer tareaId;
    private String numeroTarea;
    private Integer numeroBandeja;
    private Integer slotBandeja;
    private String trimestre;
    private LocalDate fechaAsignacion;
    private String linkTrello;
    private boolean enviado;
    private LocalDate fechaEnvio;

    public AsignacionResponse() {
    }

    public AsignacionResponse(Integer id,
                              Integer dosimetroId,
                              Integer numeroDosimetro,
                              Integer clienteId,
                              String clienteNombre,
                              Integer ejecutivoId,
                              String ejecutivoNombre,
                              Integer empresaId,
                              String empresaNombre,
                              Integer tipoPortaId,
                              String tipoPortaNombre,
                              Integer tareaId,
                              String numeroTarea,
                              Integer numeroBandeja,
                              Integer slotBandeja,
                              String trimestre,
                              LocalDate fechaAsignacion,
                              String linkTrello) {
        this.id = id;
        this.dosimetroId = dosimetroId;
        this.numeroDosimetro = numeroDosimetro;
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.ejecutivoId = ejecutivoId;
        this.ejecutivoNombre = ejecutivoNombre;
        this.empresaId = empresaId;
        this.empresaNombre = empresaNombre;
        this.tipoPortaId = tipoPortaId;
        this.tipoPortaNombre = tipoPortaNombre;
        this.tareaId = tareaId;
        this.numeroTarea = numeroTarea;
        this.numeroBandeja = numeroBandeja;
        this.slotBandeja = slotBandeja;
        this.trimestre = trimestre;
        this.fechaAsignacion = fechaAsignacion;
        this.linkTrello = linkTrello;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDosimetroId() {
        return dosimetroId;
    }

    public void setDosimetroId(Integer dosimetroId) {
        this.dosimetroId = dosimetroId;
    }

    public Integer getNumeroDosimetro() {
        return numeroDosimetro;
    }

    public void setNumeroDosimetro(Integer numeroDosimetro) {
        this.numeroDosimetro = numeroDosimetro;
    }

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteNombreCorto() {
        return clienteNombreCorto;
    }

    public void setClienteNombreCorto(String clienteNombreCorto) {
        this.clienteNombreCorto = clienteNombreCorto;
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

    public Integer getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Integer empresaId) {
        this.empresaId = empresaId;
    }

    public String getEmpresaNombre() {
        return empresaNombre;
    }

    public void setEmpresaNombre(String empresaNombre) {
        this.empresaNombre = empresaNombre;
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

    public Integer getTareaId() {
        return tareaId;
    }

    public void setTareaId(Integer tareaId) {
        this.tareaId = tareaId;
    }

    public String getNumeroTarea() {
        return numeroTarea;
    }

    public void setNumeroTarea(String numeroTarea) {
        this.numeroTarea = numeroTarea;
    }

    public Integer getNumeroBandeja() {
        return numeroBandeja;
    }

    public void setNumeroBandeja(Integer numeroBandeja) {
        this.numeroBandeja = numeroBandeja;
    }

    public Integer getSlotBandeja() {
        return slotBandeja;
    }

    public void setSlotBandeja(Integer slotBandeja) {
        this.slotBandeja = slotBandeja;
    }

    public String getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(String trimestre) {
        this.trimestre = trimestre;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }

    public void setFechaAsignacion(LocalDate fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public String getLinkTrello() {
        return linkTrello;
    }

    public void setLinkTrello(String linkTrello) {
        this.linkTrello = linkTrello;
    }

    public boolean isEnviado() {
        return enviado;
    }

    public void setEnviado(boolean enviado) {
        this.enviado = enviado;
    }

    public LocalDate getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(LocalDate fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }
}