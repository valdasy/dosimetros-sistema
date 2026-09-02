package com.dosimetros.backend.informeisp.model;

/**
 * Una fila lista para la hoja DOSIS del Excel del ISP, con los códigos ya asignados.
 */
public class FilaDosis {

    public String cliente;      // ordenar la salida + columna de ayuda en DOSIS
    public String area;         // columna de ayuda en DOSIS (llenado manual)
    public String run;          // RUT persona
    public Integer codServ;
    public String rutEntidad;
    public Integer codPrac;
    public Integer codCargo;
    public String fechaInicio;
    public String fechaFin;
    public Double dosis;        // null si va vacío
    public String observa;      // NR | NU | DD | <LD | null

    // Marca que el código fue SUGERIDO por compañeros del mismo cliente+área
    // (no confirmado por la maestra). El escritor pinta esas celdas.
    public boolean cargoSugerido;
    public boolean pracSugerido;
}
