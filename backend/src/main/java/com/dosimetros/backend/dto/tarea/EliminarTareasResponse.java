package com.dosimetros.backend.dto.tarea;

/** Resultado de eliminar tareas: cuántas tareas y cuántos dosímetros se borraron. */
public class EliminarTareasResponse {

    private int tareas;
    private int dosimetros;

    public EliminarTareasResponse() {
    }

    public EliminarTareasResponse(int tareas, int dosimetros) {
        this.tareas = tareas;
        this.dosimetros = dosimetros;
    }

    public int getTareas() {
        return tareas;
    }

    public void setTareas(int tareas) {
        this.tareas = tareas;
    }

    public int getDosimetros() {
        return dosimetros;
    }

    public void setDosimetros(int dosimetros) {
        this.dosimetros = dosimetros;
    }
}
