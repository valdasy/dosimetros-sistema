package com.dosimetros.backend.informeisp.entity;

import jakarta.persistence.*;

/**
 * Maestra aprendida por persona: (empresa, RUT) -> COD CARGO y COD PRAC.
 * El match se hace por laboratorio (Dosimet | Photomat) y RUT. Se importa
 * desde el informe ISP del trimestre anterior; no se versiona en git.
 */
@Entity
@Table(name = "isp_persona_codigo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_isp_persona_empresa_rut", columnNames = {"empresa", "rut"}))
public class IspPersonaCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String empresa;

    @Column(nullable = false, length = 15)
    private String rut;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "cod_prac")
    private Integer codPrac;

    public IspPersonaCodigo() {
    }

    public IspPersonaCodigo(String empresa, String rut, Integer codCargo, Integer codPrac) {
        this.empresa = empresa;
        this.rut = rut;
        this.codCargo = codCargo;
        this.codPrac = codPrac;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public Integer getCodCargo() {
        return codCargo;
    }

    public void setCodCargo(Integer codCargo) {
        this.codCargo = codCargo;
    }

    public Integer getCodPrac() {
        return codPrac;
    }

    public void setCodPrac(Integer codPrac) {
        this.codPrac = codPrac;
    }
}
