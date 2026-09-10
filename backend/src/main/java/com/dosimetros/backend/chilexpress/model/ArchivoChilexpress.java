package com.dosimetros.backend.chilexpress.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Resultado de leer un export de Chilexpress: filas + periodo de la consulta. */
public class ArchivoChilexpress {
    public List<FilaChilexpress> filas = new ArrayList<>();
    public LocalDate periodoDesde;
    public LocalDate periodoHasta;
}
