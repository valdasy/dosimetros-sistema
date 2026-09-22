package com.dosimetros.backend.dto.dosimetro;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Petición para "sacar por rango": quitar de una tarea los dosímetros que caen
 * dentro de un rango de bandeja/slot (por ejemplo porque se extraviaron o se
 * dañaron), dejándolos como stock en "limbo" (disponibles pero sin tarea). No
 * se elimina el dosímetro ni su historial; solo deja de ocupar su posición.
 */
public class SacarRangoRequest {

    @NotNull(message = "tareaId es obligatorio")
    private Integer tareaId;

    @NotNull(message = "bandejaDesde es obligatorio")
    @Min(value = 1, message = "bandejaDesde debe ser mayor a 0")
    private Integer bandejaDesde;

    @NotNull(message = "bandejaHasta es obligatorio")
    @Min(value = 1, message = "bandejaHasta debe ser mayor a 0")
    private Integer bandejaHasta;

    // null = no filtrar por slot (aplica a toda la bandeja)
    private Integer slotDesde;
    private Integer slotHasta;

    public SacarRangoRequest() {}

    public Integer getTareaId() { return tareaId; }
    public void setTareaId(Integer tareaId) { this.tareaId = tareaId; }

    public Integer getBandejaDesde() { return bandejaDesde; }
    public void setBandejaDesde(Integer bandejaDesde) { this.bandejaDesde = bandejaDesde; }

    public Integer getBandejaHasta() { return bandejaHasta; }
    public void setBandejaHasta(Integer bandejaHasta) { this.bandejaHasta = bandejaHasta; }

    public Integer getSlotDesde() { return slotDesde; }
    public void setSlotDesde(Integer slotDesde) { this.slotDesde = slotDesde; }

    public Integer getSlotHasta() { return slotHasta; }
    public void setSlotHasta(Integer slotHasta) { this.slotHasta = slotHasta; }
}
