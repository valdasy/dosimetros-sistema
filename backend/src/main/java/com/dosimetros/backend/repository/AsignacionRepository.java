package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Asignacion;
import com.dosimetros.backend.entity.TipoPorta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AsignacionRepository extends JpaRepository<Asignacion, Integer> {

    List<Asignacion> findByDosimetroIdOrderByFechaAsignacionDesc(Integer dosimetroId);

    // Cuántas asignaciones usan un tipo de porta (para proteger su eliminación).
    long countByTipoPortaId(Integer tipoPortaId);

    // Reasigna en bloque el tipo de porta del histórico de asignaciones a otra
    // porta (la "Sin armar" de la misma tecnología) al eliminar una porta en uso.
    @Modifying
    @Query("UPDATE Asignacion a SET a.tipoPorta = :destino WHERE a.tipoPorta = :origen")
    int reasignarTipoPorta(@Param("origen") TipoPorta origen, @Param("destino") TipoPorta destino);

    // Cuántas asignaciones (histórico) tiene un ejecutivo (aviso al desactivar).
    long countByEjecutivoId(Integer ejecutivoId);

    // Cuántas asignaciones (histórico) tiene un cliente (protege el borrado físico).
    long countByClienteId(Integer clienteId);

    List<Asignacion> findByEjecutivoIdOrderByTrimestreDescFechaAsignacionDesc(Integer ejecutivoId);

    List<Asignacion> findByClienteIdOrderByFechaAsignacionDesc(Integer clienteId);

    // Resumen de un cliente: total de dosímetros por trimestre y tipo de porta
    // (para el detalle del cliente sin listar todos los dosímetros).
    // Devuelve [trimestre, tipoPortaNombre, cantidad], del trimestre más reciente.
    @Query("""
        SELECT a.trimestre, tp.nombre, COUNT(a)
        FROM Asignacion a JOIN a.tipoPorta tp
        WHERE a.cliente.id = :clienteId
        GROUP BY a.trimestre, tp.nombre
        ORDER BY SUBSTRING(a.trimestre, 3, 4) DESC, SUBSTRING(a.trimestre, 1, 1) DESC, tp.nombre ASC
    """)
    List<Object[]> resumenPortaTrimestrePorCliente(@Param("clienteId") Integer clienteId);

    // Vista ejecutivo con multifiltros (todos opcionales).
    @Query("""
        SELECT a FROM Asignacion a
        WHERE a.ejecutivo.id = :ejecutivoId
          AND (:clienteId IS NULL OR a.cliente.id = :clienteId)
          AND (:trimestre IS NULL OR a.trimestre = :trimestre)
          AND (:fecha IS NULL OR a.fechaAsignacion = :fecha)
          AND (:tipoPortaId IS NULL OR a.tipoPorta.id = :tipoPortaId)
          AND (:numeroBandeja IS NULL OR a.numeroBandeja = :numeroBandeja)
          AND (:slotBandeja IS NULL OR a.slotBandeja = :slotBandeja)
          AND (:link IS NULL OR LOWER(a.linkTrello) LIKE LOWER(CONCAT('%', :link, '%')))
        ORDER BY a.trimestre DESC, a.fechaAsignacion DESC
    """)
    List<Asignacion> filtrarPorEjecutivo(
            @Param("ejecutivoId") Integer ejecutivoId,
            @Param("clienteId") Integer clienteId,
            @Param("trimestre") String trimestre,
            @Param("fecha") LocalDate fecha,
            @Param("tipoPortaId") Integer tipoPortaId,
            @Param("numeroBandeja") Integer numeroBandeja,
            @Param("slotBandeja") Integer slotBandeja,
            @Param("link") String link
    );

    // Opciones de filtro acotadas a las asignaciones del ejecutivo
    @Query("""
        SELECT DISTINCT a.trimestre FROM Asignacion a WHERE a.ejecutivo.id = :ejecutivoId
        ORDER BY SUBSTRING(a.trimestre, 3, 4) DESC, SUBSTRING(a.trimestre, 1, 1) DESC
    """)
    List<String> trimestresDeEjecutivo(@Param("ejecutivoId") Integer ejecutivoId);

    // Trimestres (distintos) de un cliente, del más reciente al más antiguo.
    // ejecutivoId opcional: si viene, se acota a las asignaciones de ese ejecutivo.
    // Sirve para poblar los chips de trimestre sin traer todas las asignaciones.
    @Query("""
        SELECT DISTINCT a.trimestre FROM Asignacion a
        WHERE a.cliente.id = :clienteId
          AND (:ejecutivoId IS NULL OR a.ejecutivo.id = :ejecutivoId)
        ORDER BY SUBSTRING(a.trimestre, 3, 4) DESC, SUBSTRING(a.trimestre, 1, 1) DESC
    """)
    List<String> trimestresDeCliente(@Param("clienteId") Integer clienteId,
                                     @Param("ejecutivoId") Integer ejecutivoId);

    @Query("""
        SELECT DISTINCT c.id, c.razonSocial FROM Asignacion a JOIN a.cliente c
        WHERE a.ejecutivo.id = :ejecutivoId ORDER BY c.razonSocial
    """)
    List<Object[]> clientesDeEjecutivo(@Param("ejecutivoId") Integer ejecutivoId);

    @Query("""
        SELECT DISTINCT tp.id, tp.nombre FROM Asignacion a JOIN a.tipoPorta tp
        WHERE a.ejecutivo.id = :ejecutivoId ORDER BY tp.nombre
    """)
    List<Object[]> portasDeEjecutivo(@Param("ejecutivoId") Integer ejecutivoId);

    // #18: asignaciones por estado de envío (pendientes o enviadas), filtrables
    // por ejecutivo y trimestre. El orden fino se hace en el frontend.
    @Query("""
        SELECT a FROM Asignacion a
        WHERE a.enviado = :enviado
          AND (:ejecutivoId IS NULL OR a.ejecutivo.id = :ejecutivoId)
          AND (:trimestre IS NULL OR a.trimestre = :trimestre)
        ORDER BY a.trimestre DESC, a.fechaAsignacion DESC, a.id DESC
    """)
    List<Asignacion> porEstadoEnvio(
            @Param("ejecutivoId") Integer ejecutivoId,
            @Param("trimestre") String trimestre,
            @Param("enviado") boolean enviado
    );

    // #18: conteo de asignaciones por cliente y trimestre (comparación por
    // trimestres / pendiente de asignación). Opcionalmente acotado a un ejecutivo.
    @Query("""
        SELECT a.cliente.id, a.cliente.razonSocial, a.cliente.nombreCorto, a.trimestre, COUNT(a)
        FROM Asignacion a
        WHERE (:ejecutivoId IS NULL OR a.ejecutivo.id = :ejecutivoId)
        GROUP BY a.cliente.id, a.cliente.razonSocial, a.cliente.nombreCorto, a.trimestre
    """)
    List<Object[]> conteoPorClienteTrimestre(@Param("ejecutivoId") Integer ejecutivoId);

    // Igual que el anterior pero desglosado por tipo de porta: permite totalizar
    // por porta los dosímetros pendientes de asignación (lo que el cliente tenía
    // en el trimestre base). Devuelve [clienteId, trimestre, portaId, portaNombre, cantidad].
    @Query("""
        SELECT a.cliente.id, a.trimestre, tp.id, tp.nombre, COUNT(a)
        FROM Asignacion a JOIN a.tipoPorta tp
        WHERE (:ejecutivoId IS NULL OR a.ejecutivo.id = :ejecutivoId)
        GROUP BY a.cliente.id, a.trimestre, tp.id, tp.nombre
    """)
    List<Object[]> conteoPorClienteTrimestrePorta(@Param("ejecutivoId") Integer ejecutivoId);

    // #14 (Correcciones): asignaciones filtrables (admin), para corregir en lote.
    @Query("""
        SELECT a FROM Asignacion a
        WHERE (:clienteId IS NULL OR a.cliente.id = :clienteId)
          AND (:ejecutivoId IS NULL OR a.ejecutivo.id = :ejecutivoId)
          AND (:empresaId IS NULL OR a.empresa.id = :empresaId)
          AND (:trimestre IS NULL OR a.trimestre = :trimestre)
          AND (:tipoPortaId IS NULL OR a.tipoPorta.id = :tipoPortaId)
          AND (:link IS NULL OR LOWER(a.linkTrello) LIKE LOWER(CONCAT('%', :link, '%')))
        ORDER BY a.trimestre DESC, a.fechaAsignacion DESC, a.id DESC
    """)
    List<Asignacion> filtrarAsignaciones(
            @Param("clienteId") Integer clienteId,
            @Param("ejecutivoId") Integer ejecutivoId,
            @Param("empresaId") Integer empresaId,
            @Param("trimestre") String trimestre,
            @Param("tipoPortaId") Integer tipoPortaId,
            @Param("link") String link
    );

    // Corrección (liberación): asignaciones de un cliente en un trimestre,
    // opcionalmente acotadas a una tarea y a un rango continuo de bandeja/slot.
    // El rango es lexicográfico por (bandeja, slot); cualquier extremo puede ser
    // null (sin límite). Las filas sin bandeja quedan fuera cuando se pide rango.
    @Query("""
        SELECT a FROM Asignacion a
        WHERE a.cliente.id = :clienteId
          AND a.trimestre = :trimestre
          AND (:tareaNumero IS NULL OR a.tarea.numeroTarea = :tareaNumero)
          AND (:desdeBandeja IS NULL
               OR a.numeroBandeja > :desdeBandeja
               OR (a.numeroBandeja = :desdeBandeja
                   AND (:desdeSlot IS NULL OR a.slotBandeja >= :desdeSlot)))
          AND (:hastaBandeja IS NULL
               OR a.numeroBandeja < :hastaBandeja
               OR (a.numeroBandeja = :hastaBandeja
                   AND (:hastaSlot IS NULL OR a.slotBandeja <= :hastaSlot)))
        ORDER BY a.tarea.id ASC, a.numeroBandeja ASC, a.slotBandeja ASC
    """)
    List<Asignacion> paraLiberar(
            @Param("clienteId") Integer clienteId,
            @Param("trimestre") String trimestre,
            @Param("tareaNumero") String tareaNumero,
            @Param("desdeBandeja") Integer desdeBandeja,
            @Param("desdeSlot") Integer desdeSlot,
            @Param("hastaBandeja") Integer hastaBandeja,
            @Param("hastaSlot") Integer hastaSlot
    );

    // Cuántas asignaciones se hicieron EN esta tarea (a.tarea apunta a ella). Es
    // lo que impide eliminarla: si se asignó en la tarea, borrarla perdería ese
    // dato del historial. El historial pasado del dosímetro (por otras tareas) no
    // cuenta, porque al eliminar la tarea el dosímetro se conserva.
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE a.tarea.id = :tareaId")
    long contarAsignacionesEnTarea(@Param("tareaId") Integer tareaId);

    // Ids de tareas donde se hizo al menos una asignación (para el listado).
    @Query("SELECT DISTINCT a.tarea.id FROM Asignacion a WHERE a.tarea IS NOT NULL")
    List<Integer> tareaIdsConAsignaciones();

    // HU #17: KPIs de asignaciones (todos opcionalmente filtrados por trimestre)
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE (:trimestre IS NULL OR a.trimestre = :trimestre)")
    long contarAsignaciones(@Param("trimestre") String trimestre);

    @Query("""
        SELECT e.id, e.nombre, COUNT(a)
        FROM Asignacion a JOIN a.empresa e
        WHERE (:trimestre IS NULL OR a.trimestre = :trimestre)
        GROUP BY e.id, e.nombre
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> contarPorEmpresa(@Param("trimestre") String trimestre);

    @Query("""
        SELECT ej.id, ej.nombre, COUNT(a)
        FROM Asignacion a JOIN a.ejecutivo ej
        WHERE (:trimestre IS NULL OR a.trimestre = :trimestre)
        GROUP BY ej.id, ej.nombre
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> contarPorEjecutivo(@Param("trimestre") String trimestre);

    @Query("""
        SELECT tp.id, tp.nombre, COUNT(a)
        FROM Asignacion a JOIN a.tipoPorta tp
        WHERE (:trimestre IS NULL OR a.trimestre = :trimestre)
        GROUP BY tp.id, tp.nombre
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> contarPorTipoPorta(@Param("trimestre") String trimestre);

    @Query("""
        SELECT c.id, c.razonSocial, COUNT(a)
        FROM Asignacion a JOIN a.cliente c
        WHERE (:trimestre IS NULL OR a.trimestre = :trimestre)
        GROUP BY c.id, c.razonSocial
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> contarPorCliente(@Param("trimestre") String trimestre);

    // Formato del trimestre: 'QTYYYY' (ej. 2T2025). Se ordena por año y luego trimestre.
    @Query("""
        SELECT a.trimestre, COUNT(a) FROM Asignacion a
        GROUP BY a.trimestre
        ORDER BY SUBSTRING(a.trimestre, 3, 4) ASC, SUBSTRING(a.trimestre, 1, 1) ASC
    """)
    List<Object[]> contarPorTrimestre();

    @Query("""
        SELECT DISTINCT a.trimestre FROM Asignacion a
        ORDER BY SUBSTRING(a.trimestre, 3, 4) ASC, SUBSTRING(a.trimestre, 1, 1) ASC
    """)
    List<String> trimestresDistinct();

    // Asignaciones de un trimestre agrupadas por mes (1-12) de la fecha de asignación.
    @Query("""
        SELECT EXTRACT(MONTH FROM a.fechaAsignacion), COUNT(a) FROM Asignacion a
        WHERE a.trimestre = :trimestre
        GROUP BY EXTRACT(MONTH FROM a.fechaAsignacion)
        ORDER BY EXTRACT(MONTH FROM a.fechaAsignacion)
    """)
    List<Object[]> contarPorMesEnTrimestre(@Param("trimestre") String trimestre);
}