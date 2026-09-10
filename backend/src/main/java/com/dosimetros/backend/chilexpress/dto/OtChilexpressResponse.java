package com.dosimetros.backend.chilexpress.dto;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Vista de una Orden de Transporte para el frontend. */
public class OtChilexpressResponse {

    private final Integer id;
    private final String empresa;
    private final String nroOt;
    private final String nroReferencia;
    private final String nombreDestinatario;
    private final String destino;
    private final String direccion;
    private final String servicio;
    private final Integer valorDeclarado;
    private final String oficinaOrigen;
    private final String oficinaDestino;
    private final String tipoAdmision;
    private final String tipoEntrega;
    private final String estado;
    private final String receptor;
    private final String rutReceptor;
    private final LocalDate fechaPrimerIntento;
    private final LocalDate fechaEntrega;
    private final String horaEntrega;
    private final String certificadoEntrega;
    private final LocalDate periodoDesde;
    private final LocalDate periodoHasta;
    private final LocalDateTime actualizadoEn;

    public OtChilexpressResponse(ChilexpressOt o) {
        this.id = o.getId();
        this.empresa = o.getEmpresa();
        this.nroOt = o.getNroOt();
        this.nroReferencia = o.getNroReferencia();
        this.nombreDestinatario = o.getNombreDestinatario();
        this.destino = o.getDestino();
        this.direccion = o.getDireccion();
        this.servicio = o.getServicio();
        this.valorDeclarado = o.getValorDeclarado();
        this.oficinaOrigen = o.getOficinaOrigen();
        this.oficinaDestino = o.getOficinaDestino();
        this.tipoAdmision = o.getTipoAdmision();
        this.tipoEntrega = o.getTipoEntrega();
        this.estado = o.getEstado();
        this.receptor = o.getReceptor();
        this.rutReceptor = o.getRutReceptor();
        this.fechaPrimerIntento = o.getFechaPrimerIntento();
        this.fechaEntrega = o.getFechaEntrega();
        this.horaEntrega = o.getHoraEntrega();
        this.certificadoEntrega = o.getCertificadoEntrega();
        this.periodoDesde = o.getPeriodoDesde();
        this.periodoHasta = o.getPeriodoHasta();
        this.actualizadoEn = o.getActualizadoEn();
    }

    public Integer getId() { return id; }
    public String getEmpresa() { return empresa; }
    public String getNroOt() { return nroOt; }
    public String getNroReferencia() { return nroReferencia; }
    public String getNombreDestinatario() { return nombreDestinatario; }
    public String getDestino() { return destino; }
    public String getDireccion() { return direccion; }
    public String getServicio() { return servicio; }
    public Integer getValorDeclarado() { return valorDeclarado; }
    public String getOficinaOrigen() { return oficinaOrigen; }
    public String getOficinaDestino() { return oficinaDestino; }
    public String getTipoAdmision() { return tipoAdmision; }
    public String getTipoEntrega() { return tipoEntrega; }
    public String getEstado() { return estado; }
    public String getReceptor() { return receptor; }
    public String getRutReceptor() { return rutReceptor; }
    public LocalDate getFechaPrimerIntento() { return fechaPrimerIntento; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public String getHoraEntrega() { return horaEntrega; }
    public String getCertificadoEntrega() { return certificadoEntrega; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
