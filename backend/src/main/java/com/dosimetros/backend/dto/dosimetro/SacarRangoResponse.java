package com.dosimetros.backend.dto.dosimetro;

/**
 * Resultado de "sacar por rango": cuántos dosímetros se sacaron (quedaron en
 * limbo: disponibles sin tarea) y cuántos se omitieron por no estar disponibles.
 */
public class SacarRangoResponse {

    private int sacados;
    private int omitidos;

    public SacarRangoResponse() {}

    public SacarRangoResponse(int sacados, int omitidos) {
        this.sacados = sacados;
        this.omitidos = omitidos;
    }

    public int getSacados() { return sacados; }
    public void setSacados(int sacados) { this.sacados = sacados; }

    public int getOmitidos() { return omitidos; }
    public void setOmitidos(int omitidos) { this.omitidos = omitidos; }
}
