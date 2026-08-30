package com.dosimetros.backend.informeisp.model;

/**
 * Un hallazgo de la Etapa 1 para la hoja REVISION (no bloquea la generación).
 */
public class Inconsistencia {

    public int filaExcel;
    public String rut;
    public String usuario;
    public String cliente;
    public String tipo;     // categoría del hallazgo
    public String detalle;  // descripción legible

    public Inconsistencia(int filaExcel, String rut, String usuario, String cliente,
                          String tipo, String detalle) {
        this.filaExcel = filaExcel;
        this.rut = rut;
        this.usuario = usuario;
        this.cliente = cliente;
        this.tipo = tipo;
        this.detalle = detalle;
    }

    public int getFilaExcel() {
        return filaExcel;
    }

    public String getRut() {
        return rut;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getCliente() {
        return cliente;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDetalle() {
        return detalle;
    }
}
