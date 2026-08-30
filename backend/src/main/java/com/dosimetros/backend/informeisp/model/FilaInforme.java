package com.dosimetros.backend.informeisp.model;

/**
 * Una fila del Informe de Dosis crudo (un servicio = un dosímetro).
 * Refleja las columnas relevantes de la hoja Informe_dosis.
 */
public class FilaInforme {

    public int filaExcel;          // número de fila en el Excel origen (para reportes)
    public String cliente;
    public String documentoCliente; // RUT de la entidad
    public String rut;              // RUT de la persona
    public String usuario;
    public String genero;
    public String tipoDosimetro;
    public String dosimetro;
    public String fechaInicio;
    public String fechaFin;
    public String dosisProfundidad; // Hp(10)
    public String dosisPiel;        // Hp(0.07)
    public String dosisCristalino;  // Hp(3)
    public String ubicacion;
    public String periodicidad;
    public String tecnologia;       // opcional: TLD | OSL | FILM (columna del informe)
}
