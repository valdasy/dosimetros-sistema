package com.dosimetros.backend.informeisp.repository;

import com.dosimetros.backend.informeisp.entity.IspClasificador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IspClasificadorRepository extends JpaRepository<IspClasificador, Integer> {

    List<IspClasificador> findByTipoOrderByCodigo(String tipo);
}
