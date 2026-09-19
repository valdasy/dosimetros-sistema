package com.dosimetros.backend.dto.tipoporta;

/** Uso de un tipo de porta, para avisar antes de eliminarlo. */
public class UsoTipoPortaResponse {

    private final long dosimetros;
    private final long asignaciones;
    private final boolean esSinArmar;      // las portas "Sin armar" no se pueden eliminar
    private final boolean tieneFallback;   // existe una porta "Sin armar" de la misma tecnología
    private final String fallbackNombre;   // nombre de esa porta "Sin armar" (destino del histórico)

    public UsoTipoPortaResponse(long dosimetros, long asignaciones, boolean esSinArmar,
                                boolean tieneFallback, String fallbackNombre) {
        this.dosimetros = dosimetros;
        this.asignaciones = asignaciones;
        this.esSinArmar = esSinArmar;
        this.tieneFallback = tieneFallback;
        this.fallbackNombre = fallbackNombre;
    }

    public long getDosimetros() { return dosimetros; }
    public long getAsignaciones() { return asignaciones; }
    public long getTotal() { return dosimetros + asignaciones; }
    public boolean isEsSinArmar() { return esSinArmar; }
    public boolean isTieneFallback() { return tieneFallback; }
    public String getFallbackNombre() { return fallbackNombre; }
}
