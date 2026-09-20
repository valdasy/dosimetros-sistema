package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Asignacion;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.entity.Empresa;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el filtro de clientes (#16): ejecutivo, empresa (vía asignaciones) y texto. */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ClienteRepositoryTest {

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private EjecutivoRepository ejecutivoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private TipoDosimetroRepository tipoDosimetroRepository;
    @Autowired private TipoPortaRepository tipoPortaRepository;
    @Autowired private DosimetroRepository dosimetroRepository;
    @Autowired private AsignacionRepository asignacionRepository;

    private Ejecutivo e1;
    private Empresa emp1;
    private Cliente acme;
    private Cliente beta;   // sin asignaciones
    private Cliente gamma;  // con asignación a OTRA empresa

    private TipoDosimetro tld;
    private TipoPorta porta;

    @BeforeEach
    void setUp() {
        e1 = ejecutivo("Juan");
        Ejecutivo e2 = ejecutivo("Pedro");
        emp1 = empresa("Photomat");
        Empresa emp2 = empresa("Dosimet");

        acme = cliente("ACME Salud", e1);
        beta = cliente("Beta Minería", e2);   // no tiene ninguna asignación
        gamma = cliente("Gamma Labs", e2);

        tld = new TipoDosimetro();
        tld.setNombre("TLD");
        tld = tipoDosimetroRepository.save(tld);
        porta = new TipoPorta();
        porta.setNombre("Porta gringo");
        porta.setTipoDosimetro(tld);
        porta = tipoPortaRepository.save(porta);

        // ACME tiene una asignación con la empresa Photomat (emp1).
        asignacion(acme, e1, emp1, 1);
        // Gamma tiene una asignación pero con OTRA empresa (emp2).
        asignacion(gamma, e2, emp2, 2);
    }

    private void asignacion(Cliente c, Ejecutivo ej, Empresa em, int numero) {
        Dosimetro d = new Dosimetro();
        d.setNumero(numero);
        d.setTipoDosimetro(tld);
        d.setEstado("asignado");
        d.setFechaCreacion(LocalDate.now());
        d = dosimetroRepository.save(d);

        Asignacion a = new Asignacion();
        a.setDosimetro(d);
        a.setCliente(c);
        a.setEjecutivo(ej);
        a.setEmpresa(em);
        a.setTipoPorta(porta);
        a.setTrimestre("2T2025");
        a.setFechaAsignacion(LocalDate.now());
        asignacionRepository.save(a);
    }

    private Ejecutivo ejecutivo(String nombre) {
        Ejecutivo e = new Ejecutivo();
        e.setNombre(nombre);
        e.setActivo(true);
        return ejecutivoRepository.save(e);
    }

    private Empresa empresa(String nombre) {
        Empresa e = new Empresa();
        e.setNombre(nombre);
        e.setActiva(true);
        return empresaRepository.save(e);
    }

    private Cliente cliente(String razon, Ejecutivo ej) {
        Cliente c = new Cliente();
        c.setRazonSocial(razon);
        c.setEjecutivo(ej);
        c.setActivo(true);
        return clienteRepository.save(c);
    }

    @Test
    void filtraPorTextoEnRazonSocial() {
        List<Cliente> r = clienteRepository.filtrar(null, null, "acme", false);
        assertEquals(1, r.size());
        assertEquals("ACME Salud", r.get(0).getRazonSocial());
    }

    @Test
    void filtraPorEjecutivo() {
        List<Cliente> r = clienteRepository.filtrar(e1.getId(), null, null, false);
        assertEquals(1, r.size());
        assertEquals(e1.getId(), r.get(0).getEjecutivo().getId());
    }

    @Test
    void filtraPorEmpresaIncluyeClientesSinAsignaciones() {
        // Filtrar por emp1 devuelve: ACME (asignación a emp1) y Beta (sin
        // asignaciones, no debe ocultarse), pero NO Gamma (asignación a otra empresa).
        List<Cliente> r = clienteRepository.filtrar(null, emp1.getId(), null, false);
        List<Integer> ids = r.stream().map(Cliente::getId).toList();
        assertTrue(ids.contains(acme.getId()), "ACME (asignación a emp1) debe aparecer");
        assertTrue(ids.contains(beta.getId()), "Beta (sin asignaciones) debe aparecer");
        assertTrue(!ids.contains(gamma.getId()), "Gamma (asignación a otra empresa) no debe aparecer");
    }

    @Test
    void sinFiltrosDevuelveTodosLosActivos() {
        List<Cliente> r = clienteRepository.filtrar(null, null, null, false);
        assertEquals(3, r.size());
    }

    @Test
    void incluirInactivosMuestraLosDadosDeBaja() {
        beta.setActivo(false);
        clienteRepository.save(beta);

        List<Cliente> activos = clienteRepository.filtrar(null, null, null, false);
        assertEquals(2, activos.size(), "sin incluirInactivos no aparece el dado de baja");

        List<Cliente> todos = clienteRepository.filtrar(null, null, null, true);
        assertEquals(3, todos.size(), "con incluirInactivos aparece también el dado de baja");
    }
}
