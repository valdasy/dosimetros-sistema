package com.dosimetros.backend.dto.asignacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corrección: libera (elimina) las asignaciones de un cliente en un trimestre,
 * opcionalmente acotadas a una tarea y a un rango continuo de bandeja/slot.
 * Al aplicarse, se BORRAN esos registros de asignación (fue un error) y sus
 * dosímetros vuelven a "disponible".
 *
 * El rango es lexicográfico por (bandeja, slot): desde (desdeBandeja, desdeSlot)
 * hasta (hastaBandeja, hastaSlot), inclusivo. Cualquier extremo puede omitirse.
 * Si se usa el rango debe indicarse también la tarea (bandeja/slot se repiten
 * entre tareas).
 */
public class LiberacionMasivaRequest {

    @NotNull(message = "Debes indicar el cliente")
    private Integer clienteId;

    @NotBlank(message = "Debes indicar el trimestre")
    private String trimestre;

    private String tareaNumero;

    private Integer desdeBandeja;
    private Integer desdeSlot;
    private Integer hastaBandeja;
    private Integer hastaSlot;

    public LiberacionMasivaRequest() {
    }

    /** ¿Se especificó algún límite de bandeja/slot? */
    public boolean tieneRango() {
        return desdeBandeja != null || desdeSlot != null
                || hastaBandeja != null || hastaSlot != null;
    }

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public String getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(String trimestre) {
        this.trimestre = trimestre;
    }

    public String getTareaNumero() {
        return tareaNumero;
    }

    public void setTareaNumero(String tareaNumero) {
        this.tareaNumero = tareaNumero;
    }

    /** ¿Se acotó a una tarea específica? (texto no vacío) */
    public boolean tieneTarea() {
        return tareaNumero != null && !tareaNumero.isBlank();
    }

    public Integer getDesdeBandeja() {
        return desdeBandeja;
    }

    public void setDesdeBandeja(Integer desdeBandeja) {
        this.desdeBandeja = desdeBandeja;
    }

    public Integer getDesdeSlot() {
        return desdeSlot;
    }

    public void setDesdeSlot(Integer desdeSlot) {
        this.desdeSlot = desdeSlot;
    }

    public Integer getHastaBandeja() {
        return hastaBandeja;
    }

    public void setHastaBandeja(Integer hastaBandeja) {
        this.hastaBandeja = hastaBandeja;
    }

    public Integer getHastaSlot() {
        return hastaSlot;
    }

    public void setHastaSlot(Integer hastaSlot) {
        this.hastaSlot = hastaSlot;
    }
}
