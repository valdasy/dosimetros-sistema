package com.dosimetros.backend.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClienteRequest {

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 300, message = "La razón social no puede superar 300 caracteres")
    private String razonSocial;

    @Size(max = 200, message = "El nombre corto no puede superar 200 caracteres")
    private String nombreCorto;

    @Size(max = 20, message = "El RUT no puede superar 20 caracteres")
    private String rut;

    // Ejecutivo responsable del cliente (opcional).
    private Integer ejecutivoId;

    public ClienteRequest() {
    }

    public Integer getEjecutivoId() {
        return ejecutivoId;
    }

    public void setEjecutivoId(Integer ejecutivoId) {
        this.ejecutivoId = ejecutivoId;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNombreCorto() {
        return nombreCorto;
    }

    public void setNombreCorto(String nombreCorto) {
        this.nombreCorto = nombreCorto;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }
}