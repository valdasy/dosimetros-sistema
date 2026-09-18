package com.dosimetros.backend.dto.asignacion;

import java.util.List;

/**
 * Vista previa de una liberación (corrección): total de asignaciones que se
 * eliminarían y su detalle agrupado por tarea y bandeja (con el rango de slots).
 * Sirve para que el administrador confirme exactamente qué va a liberar.
 */
public class LiberacionPreviewResponse {

    private int total;
    private List<Grupo> grupos;

    public LiberacionPreviewResponse() {
    }

    public LiberacionPreviewResponse(int total, List<Grupo> grupos) {
        this.total = total;
        this.grupos = grupos;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<Grupo> getGrupos() {
        return grupos;
    }

    public void setGrupos(List<Grupo> grupos) {
        this.grupos = grupos;
    }

    /** Un grupo del resumen: una tarea + bandeja, con el rango de slots y la cantidad. */
    public static class Grupo {
        private String tarea;          // número de tarea o "Sin tarea"
        private Integer numeroBandeja;  // null = sin bandeja
        private Integer slotDesde;      // menor slot del grupo (null si no hay slots)
        private Integer slotHasta;      // mayor slot del grupo
        private int cantidad;

        public Grupo() {
        }

        public Grupo(String tarea, Integer numeroBandeja, Integer slotDesde, Integer slotHasta, int cantidad) {
            this.tarea = tarea;
            this.numeroBandeja = numeroBandeja;
            this.slotDesde = slotDesde;
            this.slotHasta = slotHasta;
            this.cantidad = cantidad;
        }

        public String getTarea() {
            return tarea;
        }

        public void setTarea(String tarea) {
            this.tarea = tarea;
        }

        public Integer getNumeroBandeja() {
            return numeroBandeja;
        }

        public void setNumeroBandeja(Integer numeroBandeja) {
            this.numeroBandeja = numeroBandeja;
        }

        public Integer getSlotDesde() {
            return slotDesde;
        }

        public void setSlotDesde(Integer slotDesde) {
            this.slotDesde = slotDesde;
        }

        public Integer getSlotHasta() {
            return slotHasta;
        }

        public void setSlotHasta(Integer slotHasta) {
            this.slotHasta = slotHasta;
        }

        public int getCantidad() {
            return cantidad;
        }

        public void setCantidad(int cantidad) {
            this.cantidad = cantidad;
        }
    }
}
