package com.dosimetros.backend.informeisp.model;

/**
 * Una persona única para la hoja TOES del Excel del ISP.
 */
public class PersonaToes {

    public String run;      // RUT
    public String nombre;
    public String sexo;     // F | M
    public String fechaNacimiento;
    public String pais;

    public PersonaToes(String run, String nombre, String sexo) {
        this.run = run;
        this.nombre = nombre;
        this.sexo = sexo;
    }
}
