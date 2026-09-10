package com.dosimetros.backend.chilexpress.repository;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChilexpressOtRepository extends JpaRepository<ChilexpressOt, Integer> {

    /** Registros de un mismo (empresa, Nro. OT), del más reciente al más antiguo. */
    List<ChilexpressOt> findByEmpresaAndNroOtOrderByCreadoEnDesc(String empresa, String nroOt);

    /** Panel: disponibles para retiro en sucursal. Estado real de Chilexpress:
     *  "EN RECEPCION" (distinto de "EN PRE-RECEPCION"); se toleran variantes. */
    @Query("SELECT o FROM ChilexpressOt o WHERE "
            + "LOWER(o.estado) LIKE '%sucursal%' OR LOWER(o.estado) LIKE '%retiro%' OR "
            + "(LOWER(o.estado) LIKE '%recepcion%' AND LOWER(o.estado) NOT LIKE '%pre%') "
            + "ORDER BY o.actualizadoEn DESC")
    List<ChilexpressOt> retiroEnSucursal();

    /** Panel: OT con problema (devolución, extraviada, dañada, siniestro, rechazo…). */
    @Query("SELECT o FROM ChilexpressOt o WHERE "
            + "LOWER(o.estado) LIKE '%devuel%' OR LOWER(o.estado) LIKE '%devol%' OR "
            + "LOWER(o.estado) LIKE '%extrav%' OR LOWER(o.estado) LIKE '%perdid%' OR "
            + "LOWER(o.estado) LIKE '%da_ad%' OR LOWER(o.estado) LIKE '%siniest%' OR "
            + "LOWER(o.estado) LIKE '%rechaz%' OR LOWER(o.estado) LIKE '%incidenc%' OR "
            + "LOWER(o.estado) LIKE '%no entreg%' "
            + "ORDER BY o.actualizadoEn DESC")
    List<ChilexpressOt> conProblema();

    /** Panel: OT aún sin entregar (sin fecha de entrega). En el controlador se
     *  excluyen las que ya están en sucursal, con problema o entregadas. */
    List<ChilexpressOt> findByFechaEntregaIsNullOrderByActualizadoEnDesc();

    /** Nombres de destinatario distintos (para el autocompletado del buscador). */
    @Query("SELECT DISTINCT o.nombreDestinatario FROM ChilexpressOt o "
            + "WHERE o.nombreDestinatario IS NOT NULL AND o.nombreDestinatario <> '' "
            + "AND (:empresa IS NULL OR o.empresa = :empresa) "
            + "ORDER BY o.nombreDestinatario")
    List<String> clientesDistinct(@Param("empresa") String empresa);

    /**
     * Búsqueda del listado. Combina texto (destinatario/referencia/OT), empresa,
     * rango de fecha (por entrega o por periodo de carga) y categoría de estado.
     * El tope de resultados se controla con el Pageable (evita traer todo).
     *
     * estadoCat: null · 'entregado' · 'creada' · 'viaje' · 'retiro' · 'problema'.
     */
    @Query("SELECT o FROM ChilexpressOt o WHERE "
            + "(:empresa IS NULL OR o.empresa = :empresa) AND "
            + "(:q IS NULL OR ("
            + "  LOWER(o.nombreDestinatario) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  LOWER(o.nroReferencia) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  o.nroOt LIKE CONCAT('%', :q, '%'))) AND "
            + "( (:porEntrega = TRUE AND (:desde IS NULL OR o.fechaEntrega >= :desde) "
            + "     AND (:hasta IS NULL OR o.fechaEntrega <= :hasta)) "
            + "  OR (:porEntrega = FALSE AND (:desde IS NULL OR o.periodoHasta IS NULL OR o.periodoHasta >= :desde) "
            + "     AND (:hasta IS NULL OR o.periodoDesde IS NULL OR o.periodoDesde <= :hasta)) ) AND "
            + "(:estadoCat IS NULL "
            + "  OR (:estadoCat = 'entregado' AND LOWER(o.estado) LIKE '%descargo%') "
            + "  OR (:estadoCat = 'creada' AND LOWER(o.estado) LIKE '%pre%recepcion%') "
            + "  OR (:estadoCat = 'viaje' AND LOWER(o.estado) LIKE '%conten%') "
            + "  OR (:estadoCat = 'retiro' AND LOWER(o.estado) LIKE '%recepcion%' AND LOWER(o.estado) NOT LIKE '%pre%') "
            + "  OR (:estadoCat = 'problema' AND ("
            + "        LOWER(o.estado) LIKE '%devuel%' OR LOWER(o.estado) LIKE '%devol%' OR "
            + "        LOWER(o.estado) LIKE '%extrav%' OR LOWER(o.estado) LIKE '%perdid%' OR "
            + "        LOWER(o.estado) LIKE '%da_ad%' OR LOWER(o.estado) LIKE '%siniest%' OR "
            + "        LOWER(o.estado) LIKE '%rechaz%' OR LOWER(o.estado) LIKE '%incidenc%' OR "
            + "        LOWER(o.estado) LIKE '%no entreg%')) ) "
            + "ORDER BY o.actualizadoEn DESC")
    List<ChilexpressOt> buscar(@Param("empresa") String empresa, @Param("q") String q,
                               @Param("porEntrega") boolean porEntrega,
                               @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                               @Param("estadoCat") String estadoCat, Pageable pageable);
}
