package com.dosimetros.backend.informeisp.dto;

/** Estado de las maestras cargadas (para mostrar en la app). */
public class EstadoMaestraResponse {

    private final long personas;
    private final long clientes;

    public EstadoMaestraResponse(long personas, long clientes) {
        this.personas = personas;
        this.clientes = clientes;
    }

    public long getPersonas() {
        return personas;
    }

    public long getClientes() {
        return clientes;
    }
}
