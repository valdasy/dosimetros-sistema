package com.dosimetros.backend.informeisp.entity;

import jakarta.persistence.*;

/**
 * Maestra aprendida por cliente: (empresa, RUT de la entidad) -> tecnología.
 * Se importa desde un informe ISP entregado; no se versiona en git.
 */
@Entity
@Table(name = "isp_cliente_tecnologia")
public class IspClienteTecnologia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String empresa;

    @Column(name = "rut_entidad", nullable = false, length = 15)
    private String rutEntidad;

    @Column(nullable = false, length = 20)
    private String tecnologia;

    public IspClienteTecnologia() {
    }

    public IspClienteTecnologia(String empresa, String rutEntidad, String tecnologia) {
        this.empresa = empresa;
        this.rutEntidad = rutEntidad;
        this.tecnologia = tecnologia;
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

    public String getRutEntidad() {
        return rutEntidad;
    }

    public void setRutEntidad(String rutEntidad) {
        this.rutEntidad = rutEntidad;
    }

    public String getTecnologia() {
        return tecnologia;
    }

    public void setTecnologia(String tecnologia) {
        this.tecnologia = tecnologia;
    }
}
