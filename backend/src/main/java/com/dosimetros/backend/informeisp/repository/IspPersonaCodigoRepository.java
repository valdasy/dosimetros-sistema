package com.dosimetros.backend.informeisp.repository;

import com.dosimetros.backend.informeisp.entity.IspPersonaCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IspPersonaCodigoRepository extends JpaRepository<IspPersonaCodigo, Integer> {

    Optional<IspPersonaCodigo> findByRut(String rut);
}
