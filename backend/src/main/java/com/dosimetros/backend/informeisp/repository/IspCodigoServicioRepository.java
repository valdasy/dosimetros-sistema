package com.dosimetros.backend.informeisp.repository;

import com.dosimetros.backend.informeisp.entity.IspCodigoServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IspCodigoServicioRepository extends JpaRepository<IspCodigoServicio, Integer> {

    Optional<IspCodigoServicio> findByEmpresaIdAndTecnologiaAndMagnitudAndPeriodicidad(
            Integer empresaId, String tecnologia, String magnitud, String periodicidad);
}
