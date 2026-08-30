package com.dosimetros.backend.informeisp.entity;

import jakarta.persistence.*;

/**
 * Catálogo de Códigos de Servicio RND: la combinación
 * (empresa, tecnología, magnitud, periodicidad) determina un código.
 */
@Entity
@Table(name = "isp_codigo_servicio")
public class IspCodigoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 20)
    private String tecnologia;

    @Column(nullable = false, length = 10)
    private String magnitud;

    @Column(nullable = false, length = 20)
    private String periodicidad;

    @Column(nullable = false)
    private Integer codigo;

    public IspCodigoServicio() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Integer empresaId) {
        this.empresaId = empresaId;
    }

    public String getTecnologia() {
        return tecnologia;
    }

    public void setTecnologia(String tecnologia) {
        this.tecnologia = tecnologia;
    }

    public String getMagnitud() {
        return magnitud;
    }

    public void setMagnitud(String magnitud) {
        this.magnitud = magnitud;
    }

    public String getPeriodicidad() {
        return periodicidad;
    }

    public void setPeriodicidad(String periodicidad) {
        this.periodicidad = periodicidad;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }
}
