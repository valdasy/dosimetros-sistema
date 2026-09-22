package com.dosimetros.backend.dto.dosimetro;

import java.util.List;

/**
 * Vista previa de "sacar por rango": qué dosímetros caen en el rango, cuántos se
 * pueden sacar (están disponibles) y cuántos se omitirán (no disponibles).
 */
public class SacarRangoPreviewResponse {

    private int total;
    private int sacables;
    private int omitidos;
    private List<Item> items;

    public SacarRangoPreviewResponse() {}

    public SacarRangoPreviewResponse(int total, int sacables, int omitidos, List<Item> items) {
        this.total = total;
        this.sacables = sacables;
        this.omitidos = omitidos;
        this.items = items;
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getSacables() { return sacables; }
    public void setSacables(int sacables) { this.sacables = sacables; }

    public int getOmitidos() { return omitidos; }
    public void setOmitidos(int omitidos) { this.omitidos = omitidos; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /** Un dosímetro dentro del rango con su posición y si se puede sacar. */
    public static class Item {
        private Integer id;
        private Integer numero;
        private String tipoDosimetro;
        private Integer numeroBandeja;
        private Integer slotBandeja;
        private String estado;
        private boolean sacable;

        public Item() {}

        public Item(Integer id, Integer numero, String tipoDosimetro, Integer numeroBandeja,
                    Integer slotBandeja, String estado, boolean sacable) {
            this.id = id;
            this.numero = numero;
            this.tipoDosimetro = tipoDosimetro;
            this.numeroBandeja = numeroBandeja;
            this.slotBandeja = slotBandeja;
            this.estado = estado;
            this.sacable = sacable;
        }

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public Integer getNumero() { return numero; }
        public void setNumero(Integer numero) { this.numero = numero; }

        public String getTipoDosimetro() { return tipoDosimetro; }
        public void setTipoDosimetro(String tipoDosimetro) { this.tipoDosimetro = tipoDosimetro; }

        public Integer getNumeroBandeja() { return numeroBandeja; }
        public void setNumeroBandeja(Integer numeroBandeja) { this.numeroBandeja = numeroBandeja; }

        public Integer getSlotBandeja() { return slotBandeja; }
        public void setSlotBandeja(Integer slotBandeja) { this.slotBandeja = slotBandeja; }

        public String getEstado() { return estado; }
        public void setEstado(String estado) { this.estado = estado; }

        public boolean isSacable() { return sacable; }
        public void setSacable(boolean sacable) { this.sacable = sacable; }
    }
}
