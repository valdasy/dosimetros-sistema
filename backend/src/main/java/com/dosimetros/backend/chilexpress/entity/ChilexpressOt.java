package com.dosimetros.backend.chilexpress.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Orden de Transporte (OT) de Chilexpress. Módulo autocontenido: no tiene
 * relación con las demás tablas del sistema. La empresa (laboratorio) y el
 * cliente (destinatario / referencia) se guardan como texto propio.
 */
@Entity
@Table(name = "chilexpress_ot")
public class ChilexpressOt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String empresa;

    // --- Identidad y datos fijos ---
    @Column(name = "nro_ot", nullable = false, length = 40)
    private String nroOt;

    @Column(name = "ot_padre", length = 40)
    private String otPadre;

    @Column(name = "nro_referencia", length = 300)
    private String nroReferencia;

    @Column(name = "nombre_destinatario", length = 300)
    private String nombreDestinatario;

    @Column(length = 150)
    private String destino;

    @Column(length = 400)
    private String direccion;

    @Column(length = 120)
    private String servicio;

    @Column(name = "valor_declarado")
    private Integer valorDeclarado;

    @Column(name = "oficina_origen", length = 150)
    private String oficinaOrigen;

    @Column(name = "tipo_admision", length = 60)
    private String tipoAdmision;

    @Column(name = "tipo_entrega", length = 60)
    private String tipoEntrega;

    // --- Campos que se actualizan en cada carga ---
    @Column(length = 120)
    private String estado;

    @Column(name = "oficina_destino", length = 150)
    private String oficinaDestino;

    @Column(length = 200)
    private String receptor;

    @Column(name = "rut_receptor", length = 30)
    private String rutReceptor;

    @Column(name = "fecha_primer_intento")
    private LocalDate fechaPrimerIntento;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    @Column(name = "hora_entrega", length = 10)
    private String horaEntrega;

    @Column(name = "certificado_entrega", length = 500)
    private String certificadoEntrega;

    // --- Control ---
    @Column(name = "periodo_desde")
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta")
    private LocalDate periodoHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public ChilexpressOt() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getNroOt() { return nroOt; }
    public void setNroOt(String nroOt) { this.nroOt = nroOt; }
    public String getOtPadre() { return otPadre; }
    public void setOtPadre(String otPadre) { this.otPadre = otPadre; }
    public String getNroReferencia() { return nroReferencia; }
    public void setNroReferencia(String nroReferencia) { this.nroReferencia = nroReferencia; }
    public String getNombreDestinatario() { return nombreDestinatario; }
    public void setNombreDestinatario(String nombreDestinatario) { this.nombreDestinatario = nombreDestinatario; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getServicio() { return servicio; }
    public void setServicio(String servicio) { this.servicio = servicio; }
    public Integer getValorDeclarado() { return valorDeclarado; }
    public void setValorDeclarado(Integer valorDeclarado) { this.valorDeclarado = valorDeclarado; }
    public String getOficinaOrigen() { return oficinaOrigen; }
    public void setOficinaOrigen(String oficinaOrigen) { this.oficinaOrigen = oficinaOrigen; }
    public String getTipoAdmision() { return tipoAdmision; }
    public void setTipoAdmision(String tipoAdmision) { this.tipoAdmision = tipoAdmision; }
    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getOficinaDestino() { return oficinaDestino; }
    public void setOficinaDestino(String oficinaDestino) { this.oficinaDestino = oficinaDestino; }
    public String getReceptor() { return receptor; }
    public void setReceptor(String receptor) { this.receptor = receptor; }
    public String getRutReceptor() { return rutReceptor; }
    public void setRutReceptor(String rutReceptor) { this.rutReceptor = rutReceptor; }
    public LocalDate getFechaPrimerIntento() { return fechaPrimerIntento; }
    public void setFechaPrimerIntento(LocalDate fechaPrimerIntento) { this.fechaPrimerIntento = fechaPrimerIntento; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public String getHoraEntrega() { return horaEntrega; }
    public void setHoraEntrega(String horaEntrega) { this.horaEntrega = horaEntrega; }
    public String getCertificadoEntrega() { return certificadoEntrega; }
    public void setCertificadoEntrega(String certificadoEntrega) { this.certificadoEntrega = certificadoEntrega; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
