package com.dosimetros.backend.informeisp.entity;

import jakarta.persistence.*;

/**
 * Maestra aprendida por persona: RUT -> COD CARGO y COD PRAC.
 * Se importa desde un informe ISP entregado; no se versiona en git.
 */
@Entity
@Table(name = "isp_persona_codigo")
public class IspPersonaCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 15, unique = true)
    private String rut;

    @Column(name = "cod_cargo")
    private Integer codCargo;

    @Column(name = "cod_prac")
    private Integer codPrac;

    public IspPersonaCodigo() {
    }

    public IspPersonaCodigo(String rut, Integer codCargo, Integer codPrac) {
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
