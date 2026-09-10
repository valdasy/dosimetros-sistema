package com.dosimetros.backend.chilexpress.model;

import java.time.LocalDate;

/** Una fila del export de Chilexpress ya parseada (una Orden de Transporte). */
public class FilaChilexpress {
    public String nroOt;
    public String otPadre;
    public String nroReferencia;
    public String nombreDestinatario;
    public String destino;
    public String direccion;
    public String servicio;
    public Integer valorDeclarado;
    public String oficinaOrigen;
    public String oficinaDestino;
    public String tipoAdmision;
    public String tipoEntrega;
    public String estado;
    public String receptor;
    public String rutReceptor;
    public LocalDate fechaPrimerIntento;
    public LocalDate fechaEntrega;
    public String horaEntrega;
    public String certificadoEntrega;
}
