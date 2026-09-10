package com.dosimetros.backend.chilexpress.repository;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChilexpressOtRepository extends JpaRepository<ChilexpressOt, Integer> {

    /** Registros de un mismo (empresa, Nro. OT), del más reciente al más antiguo. */
    List<ChilexpressOt> findByEmpresaAndNroOtOrderByCreadoEnDesc(String empresa, String nroOt);

    /** Búsqueda por texto (destinatario / referencia / OT) y rango sobre Fecha Entrega. */
    @Query("SELECT o FROM ChilexpressOt o WHERE "
            + "(:empresa IS NULL OR o.empresa = :empresa) AND "
            + "(:q IS NULL OR ("
            + "  LOWER(o.nombreDestinatario) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  LOWER(o.nroReferencia) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  o.nroOt LIKE CONCAT('%', :q, '%'))) AND "
            + "(:desde IS NULL OR o.fechaEntrega >= :desde) AND "
            + "(:hasta IS NULL OR o.fechaEntrega <= :hasta) "
            + "ORDER BY o.creadoEn DESC")
    List<ChilexpressOt> buscarPorEntrega(@Param("empresa") String empresa, @Param("q") String q,
                                         @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Búsqueda por texto y solapamiento con el periodo (rango de carga) del archivo. */
    @Query("SELECT o FROM ChilexpressOt o WHERE "
            + "(:empresa IS NULL OR o.empresa = :empresa) AND "
            + "(:q IS NULL OR ("
            + "  LOWER(o.nombreDestinatario) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  LOWER(o.nroReferencia) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "  o.nroOt LIKE CONCAT('%', :q, '%'))) AND "
            + "(:desde IS NULL OR o.periodoHasta IS NULL OR o.periodoHasta >= :desde) AND "
            + "(:hasta IS NULL OR o.periodoDesde IS NULL OR o.periodoDesde <= :hasta) "
            + "ORDER BY o.creadoEn DESC")
    List<ChilexpressOt> buscarPorPeriodo(@Param("empresa") String empresa, @Param("q") String q,
                                         @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
