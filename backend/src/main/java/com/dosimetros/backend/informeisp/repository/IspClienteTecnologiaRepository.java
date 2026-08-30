package com.dosimetros.backend.informeisp.repository;

import com.dosimetros.backend.informeisp.entity.IspClienteTecnologia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IspClienteTecnologiaRepository extends JpaRepository<IspClienteTecnologia, Integer> {

    Optional<IspClienteTecnologia> findByEmpresaAndRutEntidad(String empresa, String rutEntidad);
}
