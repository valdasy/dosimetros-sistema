package com.dosimetros.backend.chilexpress;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica la clasificación del panel (retiro en sucursal / con problema) por estado. */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ChilexpressOtRepositoryTest {

    @Autowired
    private ChilexpressOtRepository repo;

    private void guardar(String ot, String estado, LocalDate fechaEntrega) {
        ChilexpressOt o = new ChilexpressOt();
        o.setEmpresa("Dosimet");
        o.setNroOt(ot);
        o.setEstado(estado);
        o.setFechaEntrega(fechaEntrega);
        o.setCreadoEn(LocalDateTime.now());
        o.setActualizadoEn(LocalDateTime.now());
        repo.save(o);
    }

    @Test
    void clasificaRetiroProblemaYPendientesPorEstado() {
        guardar("1", "EN SUCURSAL PARA RETIRO", null);
        guardar("2", "DISPONIBLE EN OFICINA PARA RETIRO", null);
        guardar("3", "DEVUELTO A ORIGEN", null);
        guardar("4", "EXTRAVIADO", null);
        guardar("5", "DAÑADO", null);                 // prueba el comodín de la ñ
        guardar("6", "ENTREGADO", LocalDate.now());   // entregada -> no pendiente
        guardar("7", "EN PRE-RECEPCION", null);       // en tránsito -> pendiente

        List<ChilexpressOt> retiro = repo.retiroEnSucursal();
        List<ChilexpressOt> problema = repo.conProblema();
        List<ChilexpressOt> sinEntrega = repo.findByFechaEntregaIsNullOrderByActualizadoEnDesc();

        assertEquals(2, retiro.size());
        assertTrue(retiro.stream().allMatch(o -> List.of("1", "2").contains(o.getNroOt())));

        assertEquals(3, problema.size());
        assertTrue(problema.stream().allMatch(o -> List.of("3", "4", "5").contains(o.getNroOt())));

        // Sin fecha de entrega: 1,2,3,4,5,7 (la 6 está entregada).
        assertEquals(6, sinEntrega.size());
        assertTrue(sinEntrega.stream().noneMatch(o -> o.getNroOt().equals("6")));
        // La 7 (EN PRE-RECEPCION) es pendiente pura: no está en sucursal ni con problema.
        assertTrue(sinEntrega.stream().anyMatch(o -> o.getNroOt().equals("7")));
    }

    @Test
    void clientesDistinctDevuelveNombresUnicosOrdenados() {
        ChilexpressOt a = new ChilexpressOt();
        a.setEmpresa("Dosimet");
        a.setNroOt("10");
        a.setNombreDestinatario("HOSPITAL B");
        a.setCreadoEn(LocalDateTime.now());
        a.setActualizadoEn(LocalDateTime.now());
        repo.save(a);

        ChilexpressOt b = new ChilexpressOt();
        b.setEmpresa("Dosimet");
        b.setNroOt("11");
        b.setNombreDestinatario("HOSPITAL B"); // repetido -> distinct
        b.setCreadoEn(LocalDateTime.now());
        b.setActualizadoEn(LocalDateTime.now());
        repo.save(b);

        ChilexpressOt c = new ChilexpressOt();
        c.setEmpresa("Photomat");
        c.setNroOt("12");
        c.setNombreDestinatario("CLINICA A");
        c.setCreadoEn(LocalDateTime.now());
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);

        assertEquals(List.of("CLINICA A", "HOSPITAL B"), repo.clientesDistinct(null));
        assertEquals(List.of("HOSPITAL B"), repo.clientesDistinct("Dosimet"));
    }
}
