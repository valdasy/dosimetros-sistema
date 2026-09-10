package com.dosimetros.backend.chilexpress.dto;

import java.util.List;

/** Panel de alertas: OT en sucursal para retiro, con problema y pendientes de entrega. */
public class PanelChilexpressResponse {

    private final List<OtChilexpressResponse> retiroSucursal;
    private final List<OtChilexpressResponse> revision;
    private final List<OtChilexpressResponse> pendientes;

    public PanelChilexpressResponse(List<OtChilexpressResponse> retiroSucursal,
                                    List<OtChilexpressResponse> revision,
                                    List<OtChilexpressResponse> pendientes) {
        this.retiroSucursal = retiroSucursal;
        this.revision = revision;
        this.pendientes = pendientes;
    }

    public List<OtChilexpressResponse> getRetiroSucursal() { return retiroSucursal; }
    public List<OtChilexpressResponse> getRevision() { return revision; }
    public List<OtChilexpressResponse> getPendientes() { return pendientes; }
}
