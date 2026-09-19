package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByActivoTrue();

    // Búsqueda por nombre para el buscador del frontend
    List<Cliente> findByRazonSocialContainingIgnoreCaseAndActivoTrue(String razonSocial);

    // Coincidencia exacta por razón social (carga de asignaciones por archivo, #12)
    List<Cliente> findByRazonSocialIgnoreCaseAndActivoTrue(String razonSocial);

    // Clientes cuyo ejecutivo responsable es el indicado (HU #3 / #16)
    List<Cliente> findByEjecutivoIdAndActivoTrueOrderByRazonSocialAsc(Integer ejecutivoId);

    // Cuántos clientes activos tiene asignado un ejecutivo (aviso al desactivar).
    long countByEjecutivoIdAndActivoTrue(Integer ejecutivoId);

    // #16: clientes activos filtrados por ejecutivo, empresa (vía asignaciones)
    // y texto de búsqueda (razón social o nombre fantasía). Todos opcionales.
    @Query("""
        SELECT c FROM Cliente c
        WHERE c.activo = true
          AND (:ejecutivoId IS NULL OR c.ejecutivo.id = :ejecutivoId)
          AND (:q IS NULL
               OR LOWER(c.razonSocial) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(c.nombreCorto, '')) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:empresaId IS NULL OR EXISTS (
               SELECT 1 FROM Asignacion a
               WHERE a.cliente.id = c.id AND a.empresa.id = :empresaId))
        ORDER BY c.razonSocial ASC
    """)
    List<Cliente> filtrar(
            @Param("ejecutivoId") Integer ejecutivoId,
            @Param("empresaId") Integer empresaId,
            @Param("q") String q
    );

    // Ids de clientes que actualmente son el último destino de algún dosímetro
    // en estado 'asignado' (es decir, tienen dosímetros vigentes).
    @Query("""
        SELECT DISTINCT a.cliente.id FROM Asignacion a
        WHERE a.dosimetro.estado = 'asignado'
          AND a.id = (SELECT MAX(a2.id) FROM Asignacion a2 WHERE a2.dosimetro.id = a.dosimetro.id)
    """)
    List<Integer> findIdsClientesConDosimetroVigente();
}
