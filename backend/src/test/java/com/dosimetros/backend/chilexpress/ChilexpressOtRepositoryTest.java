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

    private void guardar(String ot, String estado, String tipoEntrega, LocalDate fechaEntrega) {
        ChilexpressOt o = new ChilexpressOt();
        o.setEmpresa("Dosimet");
        o.setNroOt(ot);
        o.setEstado(estado);
        o.setTipoEntrega(tipoEntrega);
        o.setFechaEntrega(fechaEntrega);
        o.setCreadoEn(LocalDateTime.now());
        o.setActualizadoEn(LocalDateTime.now());
        repo.save(o);
    }

    @Test
    void clasificaRetiroProblemaYPendientesPorEstado() {
        guardar("1", "EN SUCURSAL PARA RETIRO", "OFICINA", null);
        guardar("2", "EN RECEPCION", "OFICINA", null);   // en oficina + recepción -> retiro
        guardar("3", "DEVUELTO A ORIGEN", "DOMICILIO", null);
        guardar("4", "EXTRAVIADO", "DOMICILIO", null);
        guardar("5", "DAÑADO", "DOMICILIO", null);       // prueba el comodín de la ñ
        guardar("6", "EN DESCARGO", "DOMICILIO", LocalDate.now()); // entregada -> no pendiente
        guardar("7", "EN PRE-RECEPCION", "OFICINA", null); // creada, no recibida -> pendiente
        guardar("8", "EN SOBRANCIA", "DOMICILIO", null);   // inconveniente en despacho -> problema
        guardar("9", "EN RECEPCION", "DOMICILIO", null);   // recepción pero a DOMICILIO -> NO retiro

        List<ChilexpressOt> retiro = repo.retiroEnSucursal();
        List<ChilexpressOt> problema = repo.conProblema();
        List<ChilexpressOt> sinEntrega = repo.findByFechaEntregaIsNullOrderByActualizadoEnDesc();

        assertEquals(2, retiro.size());
        assertTrue(retiro.stream().allMatch(o -> List.of("1", "2").contains(o.getNroOt())));
        // "EN PRE-RECEPCION" NO es retiro (comparte "recepcion" pero tiene "pre").
        assertTrue(retiro.stream().noneMatch(o -> o.getNroOt().equals("7")));
        // "EN RECEPCION" a DOMICILIO NO es retiro (va camino al domicilio).
        assertTrue(retiro.stream().noneMatch(o -> o.getNroOt().equals("9")));

        assertEquals(4, problema.size());
        assertTrue(problema.stream().allMatch(o -> List.of("3", "4", "5", "8").contains(o.getNroOt())));
        // "EN SOBRANCIA" (inconveniente en despacho) también es problema.
        assertTrue(problema.stream().anyMatch(o -> o.getNroOt().equals("8")));

        // Sin fecha de entrega: 1,2,3,4,5,7,8,9 (la 6 está entregada).
        assertEquals(8, sinEntrega.size());
        assertTrue(sinEntrega.stream().noneMatch(o -> o.getNroOt().equals("6")));
        assertTrue(sinEntrega.stream().anyMatch(o -> o.getNroOt().equals("7")));
    }

    @Test
    void buscaPorCategoriaDeEstado() {
        guardar("A", "EN RECEPCION", "OFICINA", null);      // retiro (en oficina)
        guardar("B", "EN PRE-RECEPCION", "OFICINA", null);  // creada
        guardar("C", "EN CONTENEDOR", "DOMICILIO", null);   // viaje
        guardar("D", "EN DESCARGO", "DOMICILIO", LocalDate.now()); // entregado
        guardar("E", "EN RECEPCION", "DOMICILIO", null);    // recepción a domicilio -> NO retiro

        var todo = org.springframework.data.domain.PageRequest.of(0, 100);
        // 'retiro' trae EN RECEPCION en OFICINA, pero NO EN PRE-RECEPCION ni a DOMICILIO.
        List<ChilexpressOt> retiro = repo.buscar(null, null, true, null, null, "retiro", todo);
        assertEquals(1, retiro.size());
        assertEquals("A", retiro.get(0).getNroOt());
        // 'creada' trae solo EN PRE-RECEPCION.
        List<ChilexpressOt> creada = repo.buscar(null, null, true, null, null, "creada", todo);
        assertEquals(1, creada.size());
        assertEquals("B", creada.get(0).getNroOt());
        // 'viaje' trae EN CONTENEDOR.
        assertEquals("C", repo.buscar(null, null, true, null, null, "viaje", todo).get(0).getNroOt());
        // 'entregado' trae EN DESCARGO.
        assertEquals("D", repo.buscar(null, null, true, null, null, "entregado", todo).get(0).getNroOt());
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
