package com.dosimetros.backend.dto.ejecutivo;

/** Impacto de desactivar un ejecutivo (el histórico se conserva; es solo informativo). */
public class UsoEjecutivoResponse {

    private final long clientes;      // clientes activos a su cargo
    private final long asignaciones;  // asignaciones históricas asociadas

    public UsoEjecutivoResponse(long clientes, long asignaciones) {
        this.clientes = clientes;
        this.asignaciones = asignaciones;
    }

    public long getClientes() { return clientes; }
    public long getAsignaciones() { return asignaciones; }
}
