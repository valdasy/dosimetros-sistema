package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Tarea;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifica contra H2 las agregaciones de stock de #5 (todas las portas,
 * incluidas las que están en 0) y #6 (matriz tarea × porta).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DosimetroRepositoryTest {

    @Autowired
    private DosimetroRepository dosimetroRepository;

    @Autowired
    private TipoDosimetroRepository tipoDosimetroRepository;

    @Autowired
    private TipoPortaRepository tipoPortaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    private TipoDosimetro tipoDosimetro(String nombre) {
        TipoDosimetro t = new TipoDosimetro();
        t.setNombre(nombre);
        return tipoDosimetroRepository.save(t);
    }

    private TipoPorta tipoPorta(String nombre, TipoDosimetro td) {
        TipoPorta tp = new TipoPorta();
        tp.setNombre(nombre);
        tp.setTipoDosimetro(td);
        return tipoPortaRepository.save(tp);
    }

    private Tarea tarea(String numero) {
        Tarea t = new Tarea();
        t.setNumeroTarea(numero);
        t.setFechaCreacion(LocalDate.now());
        return tareaRepository.save(t);
    }

    private void dosimetro(int numero, TipoDosimetro td, TipoPorta tp, Tarea tarea, String estado) {
        Dosimetro d = new Dosimetro();
        d.setNumero(numero);
        d.setTipoDosimetro(td);
        d.setTipoPorta(tp);
        d.setTarea(tarea);
        d.setEstado(estado);
        dosimetroRepository.save(d);
    }

    @Test
    void stockTodasLasPortasIncluyeLasQueEstanEnCero() {
        TipoDosimetro tld = tipoDosimetro("TLD");
        TipoPorta gringo = tipoPorta("Gringo", tld);
        TipoPorta viejo = tipoPorta("Viejo", tld); // sin stock disponible
        Tarea t1 = tarea("1765");

        dosimetro(1, tld, gringo, t1, "disponible");
        dosimetro(2, tld, gringo, t1, "disponible");
        dosimetro(3, tld, viejo, t1, "asignado"); // no cuenta como disponible

        List<Object[]> filas = dosimetroRepository.stockTodasLasPortas();

        // Ambas portas presentes, aunque "Viejo" tenga 0 disponibles.
        assertEquals(2, filas.size());
        long viejoCantidad = filas.stream()
                .filter(o -> viejo.getId().equals(o[0]))
                .map(o -> (Long) o[3])
                .findFirst().orElse(-1L);
        long gringoCantidad = filas.stream()
                .filter(o -> gringo.getId().equals(o[0]))
                .map(o -> (Long) o[3])
                .findFirst().orElse(-1L);
        assertEquals(0L, viejoCantidad);
        assertEquals(2L, gringoCantidad);
    }

    @Test
    void resumenArmadoCuentaSinArmarYNullComoPendientes() {
        TipoDosimetro tld = tipoDosimetro("TLD");
        TipoPorta gringo = tipoPorta("Porta gringo", tld);
        TipoPorta sinArmar = tipoPorta("Sin armar (TLD)", tld);
        Tarea t1 = tarea("1765");

        dosimetro(1, tld, gringo, t1, "disponible");   // armado (porta real)
        dosimetro(2, tld, sinArmar, t1, "disponible"); // pendiente (Sin armar)
        dosimetro(3, tld, null, t1, "disponible");     // pendiente (sin porta)

        List<Object[]> filas = dosimetroRepository.resumenArmadoPorTarea();

        assertEquals(1, filas.size());
        Object[] fila = filas.get(0);
        assertEquals(3L, ((Number) fila[2]).longValue()); // total
        assertEquals(1L, ((Number) fila[3]).longValue()); // armados (solo el de porta real)
    }

    @Test
    void osTldMismoNumeroNoSeCruzanNiSonDuplicados() {
        TipoDosimetro tld = tipoDosimetro("TLD");
        TipoDosimetro osl = tipoDosimetro("OSL");
        TipoPorta gringo = tipoPorta("Porta gringo", tld);
        TipoPorta portaOsl = tipoPorta("OSL", osl);

        // Mismo número 500 en dos tecnologías = dos dosímetros físicos distintos.
        dosimetro(500, tld, gringo, null, "disponible");
        dosimetro(500, osl, portaOsl, null, "disponible");

        // Buscar por (número + tecnología) devuelve solo el de esa tecnología.
        List<Dosimetro> tldMatch = dosimetroRepository
                .findByNumeroAndTipoDosimetroIdOrderByIdAsc(500, tld.getId());
        List<Dosimetro> oslMatch = dosimetroRepository
                .findByNumeroAndTipoDosimetroIdOrderByIdAsc(500, osl.getId());
        assertEquals(1, tldMatch.size());
        assertEquals(tld.getId(), tldMatch.get(0).getTipoDosimetro().getId());
        assertEquals(1, oslMatch.size());
        assertEquals(osl.getId(), oslMatch.get(0).getTipoDosimetro().getId());

        // 500 OSL y 500 TLD NO son duplicados entre sí (distinta tecnología).
        assertEquals(0, dosimetroRepository.findDuplicados().size());

        // Dos del mismo número Y misma tecnología sí son duplicados reales.
        dosimetro(500, tld, gringo, null, "disponible");
        assertEquals(2, dosimetroRepository.findDuplicados().size());
    }

    @Test
    void matrizTareaPortaAgrupaPorTareaYPorta() {
        TipoDosimetro tld = tipoDosimetro("TLD");
        TipoPorta gringo = tipoPorta("Gringo", tld);
        TipoPorta viejo = tipoPorta("Viejo", tld);
        Tarea t1 = tarea("1765");

        dosimetro(1, tld, gringo, t1, "disponible");
        dosimetro(2, tld, gringo, t1, "disponible");
        dosimetro(3, tld, viejo, t1, "disponible");

        List<Object[]> celdas = dosimetroRepository.matrizTareaPorta("disponible", null);

        assertEquals(2, celdas.size()); // dos celdas: (t1,gringo) y (t1,viejo)
        long gringoEnT1 = celdas.stream()
                .filter(o -> t1.getId().equals(o[0]) && gringo.getId().equals(o[2]))
                .map(o -> (Long) o[4])
                .findFirst().orElse(-1L);
        assertEquals(2L, gringoEnT1);
        assertNotNull(celdas.get(0)[1]); // numeroTarea presente
    }
}
