package com.dosimetros.backend.chilexpress.dto;

import java.util.List;

/** Panel de alertas: OT en sucursal para retiro y OT con problema. */
public class PanelChilexpressResponse {

    private final List<OtChilexpressResponse> retiroSucursal;
    private final List<OtChilexpressResponse> revision;

    public PanelChilexpressResponse(List<OtChilexpressResponse> retiroSucursal,
                                    List<OtChilexpressResponse> revision) {
        this.retiroSucursal = retiroSucursal;
        this.revision = revision;
    }

    public List<OtChilexpressResponse> getRetiroSucursal() { return retiroSucursal; }
    public List<OtChilexpressResponse> getRevision() { return revision; }
}
