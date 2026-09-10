package com.dosimetros.backend.chilexpress;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

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

    private void guardar(String ot, String estado) {
        ChilexpressOt o = new ChilexpressOt();
        o.setEmpresa("Dosimet");
        o.setNroOt(ot);
        o.setEstado(estado);
        o.setCreadoEn(LocalDateTime.now());
        o.setActualizadoEn(LocalDateTime.now());
        repo.save(o);
    }

    @Test
    void clasificaRetiroYProblemaPorEstado() {
        guardar("1", "EN SUCURSAL PARA RETIRO");
        guardar("2", "DISPONIBLE EN OFICINA PARA RETIRO");
        guardar("3", "DEVUELTO A ORIGEN");
        guardar("4", "EXTRAVIADO");
        guardar("5", "DAÑADO");            // prueba el comodín de la ñ
        guardar("6", "ENTREGADO");
        guardar("7", "EN PRE-RECEPCION");

        List<ChilexpressOt> retiro = repo.retiroEnSucursal();
        List<ChilexpressOt> problema = repo.conProblema();

        assertEquals(2, retiro.size());
        assertTrue(retiro.stream().allMatch(o -> List.of("1", "2").contains(o.getNroOt())));

        assertEquals(3, problema.size());
        assertTrue(problema.stream().allMatch(o -> List.of("3", "4", "5").contains(o.getNroOt())));

        // Los estados normales no aparecen en ningún panel.
        assertTrue(retiro.stream().noneMatch(o -> List.of("6", "7").contains(o.getNroOt())));
        assertTrue(problema.stream().noneMatch(o -> List.of("6", "7").contains(o.getNroOt())));
    }
}
