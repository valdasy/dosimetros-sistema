package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.TipoPorta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TipoPortaRepository extends JpaRepository<TipoPorta, Integer> {

    List<TipoPorta> findAllByOrderByNombreAsc();

    List<TipoPorta> findByTipoDosimetroIdOrderByNombreAsc(Integer tipoDosimetroId);

    Optional<TipoPorta> findByNombreAndTipoDosimetroId(String nombre, Integer tipoDosimetroId);

    // Porta(s) "Sin armar" de una tecnología (nombre que empieza con "Sin armar").
    @Query("SELECT tp FROM TipoPorta tp WHERE tp.tipoDosimetro.id = :tipoDosimetroId "
            + "AND LOWER(tp.nombre) LIKE 'sin armar%' ORDER BY tp.id ASC")
    List<TipoPorta> findSinArmarByTipoDosimetro(@Param("tipoDosimetroId") Integer tipoDosimetroId);
}