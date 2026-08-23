package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Dosimetro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DosimetroRepository extends JpaRepository<Dosimetro, Integer> {

    Optional<Dosimetro> findByNumeroAndEstado(Integer numero, String estado);

    List<Dosimetro> findByEstadoOrderByNumeroAsc(String estado);

    List<Dosimetro> findByNumeroOrderByIdAsc(Integer numero);

    // #7: dosímetros de una tarea ordenados por bandeja y slot (mapa de armado).
    List<Dosimetro> findByTareaIdOrderByNumeroBandejaAscSlotBandejaAsc(Integer tareaId);

    // #7: resumen de armado por tarea. Armado = porta real (no null y no
    // "Sin armar …"). Devuelve [tareaId, numeroTarea, total, armados].
    // Solo se incluyen tareas con al menos un dosímetro NO asignado a cliente
    // (estado distinto de 'asignado'); las tareas totalmente asignadas se ocultan.
    @Query("""
        SELECT t.id, t.numeroTarea, COUNT(d),
               SUM(CASE WHEN tp.id IS NOT NULL AND LOWER(tp.nombre) NOT LIKE 'sin armar%'
                        THEN 1 ELSE 0 END)
        FROM Dosimetro d JOIN d.tarea t LEFT JOIN d.tipoPorta tp
        GROUP BY t.id, t.numeroTarea
        HAVING SUM(CASE WHEN d.estado <> 'asignado' THEN 1 ELSE 0 END) > 0
        ORDER BY t.numeroTarea ASC
    """)
    List<Object[]> resumenArmadoPorTarea();

    boolean existsByNumero(Integer numero);

    // HU buscar: solo dosímetros que ya tienen al menos una asignación (fueron asignados)
    @Query("""
        SELECT d FROM Dosimetro d
        WHERE d.numero = :numero
          AND EXISTS (SELECT 1 FROM Asignacion a WHERE a.dosimetro.id = d.id)
        ORDER BY d.id ASC
    """)
    List<Dosimetro> findByNumeroConAsignacion(@Param("numero") Integer numero);

    boolean existsByNumeroAndTipoDosimetroIdAndTipoPortaIdAndEstado(
            Integer numero,
            Integer tipoDosimetroId,
            Integer tipoPortaId,
            String estado
    );

    @Query("""
        SELECT d FROM Dosimetro d
        WHERE (:tipoDosimetroId IS NULL OR d.tipoDosimetro.id = :tipoDosimetroId)
          AND (:tipoPortaId IS NULL OR d.tipoPorta.id = :tipoPortaId)
          AND (:tareaId IS NULL OR d.tarea.id = :tareaId)
          AND (:estado IS NULL OR d.estado = :estado)
        ORDER BY d.numero ASC
    """)
    List<Dosimetro> filtrar(
            @Param("tipoDosimetroId") Integer tipoDosimetroId,
            @Param("tipoPortaId") Integer tipoPortaId,
            @Param("tareaId") Integer tareaId,
            @Param("estado") String estado
    );

    @Query("""
        SELECT d FROM Dosimetro d
        WHERE d.tarea.id = :tareaId
          AND d.numeroBandeja >= :bandejaDesde
          AND d.numeroBandeja <= :bandejaHasta
          AND (:slotDesde IS NULL OR d.slotBandeja >= :slotDesde)
          AND (:slotHasta IS NULL OR d.slotBandeja <= :slotHasta)
    """)
    List<Dosimetro> findByTareaYRangoBandejaSlot(
            @Param("tareaId") Integer tareaId,
            @Param("bandejaDesde") Integer bandejaDesde,
            @Param("bandejaHasta") Integer bandejaHasta,
            @Param("slotDesde") Integer slotDesde,
            @Param("slotHasta") Integer slotHasta
    );

    // Bloqueo pesimista para evitar la condición de carrera en la asignación
    // masiva: dos peticiones concurrentes no pueden leer el mismo dosímetro como
    // disponible y entregarlo dos veces. Requiere transacción activa (la tiene
    // AsignacionService.asignarMasivo).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT d FROM Dosimetro d
        WHERE d.estado = 'disponible'
          AND d.tarea.id IN :tareaIds
          AND d.tipoDosimetro.id = :tipoDosimetroId
        ORDER BY d.tarea.id ASC, d.numeroBandeja ASC, d.slotBandeja ASC
    """)
    List<Dosimetro> findDisponiblesCompatiblesEnTareas(
            @Param("tareaIds") List<Integer> tareaIds,
            @Param("tipoDosimetroId") Integer tipoDosimetroId
    );

    // Bloqueo pesimista para la asignación individual: se toma el dosímetro por
    // id bloqueando la fila, de modo que dos peticiones concurrentes no puedan
    // pasar ambas la validación de "disponible" sobre el mismo dosímetro.
    // Requiere transacción activa (la tiene AsignacionService.crear).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dosimetro d WHERE d.id = :id")
    Optional<Dosimetro> findByIdParaAsignar(@Param("id") Integer id);

    // HU #9: dosímetros cuyo número físico se repite (más de un id con el mismo numero)
    @Query("""
        SELECT d FROM Dosimetro d
        WHERE d.numero IN (
            SELECT d2.numero FROM Dosimetro d2 GROUP BY d2.numero HAVING COUNT(d2) > 1
        )
        ORDER BY d.numero ASC, d.id ASC
    """)
    List<Dosimetro> findDuplicados();

    // HU #17: KPIs de stock
    @Query("SELECT d.estado, COUNT(d) FROM Dosimetro d GROUP BY d.estado ORDER BY d.estado")
    List<Object[]> contarPorEstado();

    @Query("""
        SELECT t.id, t.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tipoDosimetro t
        GROUP BY t.id, t.nombre
        ORDER BY t.nombre
    """)
    List<Object[]> contarPorTipoDosimetro();

    // Disponibles para asignar, desglosados por tipo de porta (estado de armado).
    @Query("""
        SELECT tp.id, tp.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tipoPorta tp
        WHERE d.estado = 'disponible'
        GROUP BY tp.id, tp.nombre
        ORDER BY COUNT(d) DESC
    """)
    List<Object[]> contarDisponiblesPorPorta();

    // Tareas con dosímetros disponibles (opcionalmente de un tipo de dosímetro).
    @Query("""
        SELECT t.id, t.numeroTarea, COUNT(d)
        FROM Dosimetro d JOIN d.tarea t
        WHERE d.estado = 'disponible'
          AND (:tipoDosimetroId IS NULL OR d.tipoDosimetro.id = :tipoDosimetroId)
        GROUP BY t.id, t.numeroTarea
        ORDER BY t.numeroTarea ASC
    """)
    List<Object[]> tareasConDisponibles(@Param("tipoDosimetroId") Integer tipoDosimetroId);

    // Detalle de portas disponibles: porta + tipo de dosímetro + cantidad.
    @Query("""
        SELECT tp.id, tp.nombre, td.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tipoPorta tp JOIN tp.tipoDosimetro td
        WHERE d.estado = 'disponible'
        GROUP BY tp.id, tp.nombre, td.nombre
        ORDER BY td.nombre ASC, COUNT(d) DESC
    """)
    List<Object[]> detallePortasDisponibles();

    // #5: TODAS las portas con su stock disponible, incluidas las que están en 0.
    @Query("""
        SELECT tp.id, tp.nombre, td.nombre,
               COUNT(d.id)
        FROM TipoPorta tp JOIN tp.tipoDosimetro td
        LEFT JOIN Dosimetro d ON d.tipoPorta.id = tp.id AND d.estado = 'disponible'
        GROUP BY tp.id, tp.nombre, td.nombre
        ORDER BY td.nombre ASC, tp.nombre ASC
    """)
    List<Object[]> stockTodasLasPortas();

    // #6: matriz tarea × tipo de porta (conteo por celda) para la vista dinámica.
    // Solo dosímetros armados (con tarea y porta); filtrable por estado y tipo.
    @Query("""
        SELECT t.id, t.numeroTarea, tp.id, tp.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tarea t JOIN d.tipoPorta tp
        WHERE (:estado IS NULL OR d.estado = :estado)
          AND (:tipoDosimetroId IS NULL OR d.tipoDosimetro.id = :tipoDosimetroId)
        GROUP BY t.id, t.numeroTarea, tp.id, tp.nombre
        ORDER BY t.numeroTarea ASC, tp.nombre ASC
    """)
    List<Object[]> matrizTareaPorta(
            @Param("estado") String estado,
            @Param("tipoDosimetroId") Integer tipoDosimetroId);

    // --- Stock histórico: dosímetros existentes a una fecha (por creación) ---

    @Query("SELECT COUNT(d) FROM Dosimetro d WHERE d.fechaCreacion <= :fecha")
    long contarCreadosHasta(@Param("fecha") LocalDate fecha);

    @Query("""
        SELECT tp.id, tp.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tipoPorta tp
        WHERE d.fechaCreacion <= :fecha
        GROUP BY tp.id, tp.nombre
        ORDER BY COUNT(d) DESC
    """)
    List<Object[]> contarPorPortaHasta(@Param("fecha") LocalDate fecha);

    @Query("""
        SELECT t.id, t.nombre, COUNT(d)
        FROM Dosimetro d JOIN d.tipoDosimetro t
        WHERE d.fechaCreacion <= :fecha
        GROUP BY t.id, t.nombre
        ORDER BY t.nombre
    """)
    List<Object[]> contarPorTipoHasta(@Param("fecha") LocalDate fecha);
}
