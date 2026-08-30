package com.dosimetros.backend.informeisp.model;

/**
 * Una fila lista para la hoja DOSIS del Excel del ISP, con los códigos ya asignados.
 */
public class FilaDosis {

    public String cliente;      // solo para ordenar la salida
    public String run;          // RUT persona
    public Integer codServ;
    public String rutEntidad;
    public Integer codPrac;
    public Integer codCargo;
    public String fechaInicio;
    public String fechaFin;
    public Double dosis;        // null si va vacío
    public String observa;      // NR | NU | DD | <LD | null
}
