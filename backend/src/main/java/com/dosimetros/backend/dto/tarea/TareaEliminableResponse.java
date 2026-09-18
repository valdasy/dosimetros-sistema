package com.dosimetros.backend.dto.tarea;

/**
 * Fila del listado para eliminar tareas (corrección de carga): número de tarea,
 * cuántos dosímetros tiene y cuántos están disponibles, y si es eliminable.
 * Solo es eliminable si TODOS sus dosímetros están disponibles y ninguno tiene
 * historial de asignación (protege datos reales).
 */
public class TareaEliminableResponse {

    private Integer tareaId;
    private String numeroTarea;
    private int totalDosimetros;
    private int disponibles;
    private boolean eliminable;
    private String motivo; // por qué NO es eliminable (null si lo es)

    public TareaEliminableResponse() {
    }

    public TareaEliminableResponse(Integer tareaId, String numeroTarea, int totalDosimetros,
                                   int disponibles, boolean eliminable, String motivo) {
        this.tareaId = tareaId;
        this.numeroTarea = numeroTarea;
        this.totalDosimetros = totalDosimetros;
        this.disponibles = disponibles;
        this.eliminable = eliminable;
        this.motivo = motivo;
    }

    public Integer getTareaId() {
        return tareaId;
    }

    public void setTareaId(Integer tareaId) {
        this.tareaId = tareaId;
    }

    public String getNumeroTarea() {
        return numeroTarea;
    }

    public void setNumeroTarea(String numeroTarea) {
        this.numeroTarea = numeroTarea;
    }

    public int getTotalDosimetros() {
        return totalDosimetros;
    }

    public void setTotalDosimetros(int totalDosimetros) {
        this.totalDosimetros = totalDosimetros;
    }

    public int getDisponibles() {
        return disponibles;
    }

    public void setDisponibles(int disponibles) {
        this.disponibles = disponibles;
    }

    public boolean isEliminable() {
        return eliminable;
    }

    public void setEliminable(boolean eliminable) {
        this.eliminable = eliminable;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
