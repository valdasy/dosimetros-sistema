package com.dosimetros.backend.informeisp.entity;

import jakarta.persistence.*;

/**
 * Clasificador del ISP: catálogo genérico (código + nombre) por tipo
 * (CARGO, PRACTICA, LOCALIZACION, SECTOR).
 */
@Entity
@Table(name = "isp_clasificador")
public class IspClasificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private Integer codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    public IspClasificador() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
