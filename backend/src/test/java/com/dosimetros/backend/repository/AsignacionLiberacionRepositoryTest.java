package com.dosimetros.backend.repository;

import com.dosimetros.backend.entity.Asignacion;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.entity.Empresa;
import com.dosimetros.backend.entity.Tarea;
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

/** Verifica la selección de asignaciones para liberar, con el rango lexicográfico de bandeja/slot. */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AsignacionLiberacionRepositoryTest {

    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private EjecutivoRepository ejecutivoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private TipoPortaRepository tipoPortaRepository;
    @Autowired private TipoDosimetroRepository tipoDosimetroRepository;
    @Autowired private TareaRepository tareaRepository;
    @Autowired private DosimetroRepository dosimetroRepository;

    private Cliente c1, c2;
    private Ejecutivo eje;
    private Empresa emp;
    private TipoPorta porta;
    private TipoDosimetro tld;
    private Tarea t1, t2;
    private int numeroSeq = 1000;

    @BeforeEach
    void setUp() {
        tld = new TipoDosimetro(); tld.setNombre("TLD"); tld = tipoDosimetroRepository.save(tld);
        porta = new TipoPorta(); porta.setNombre("Porta gringo"); porta.setTipoDosimetro(tld);
        porta = tipoPortaRepository.save(porta);
        c1 = new Cliente(); c1.setRazonSocial("Cliente 1"); c1 = clienteRepository.save(c1);
        c2 = new Cliente(); c2.setRazonSocial("Cliente 2"); c2 = clienteRepository.save(c2);
        eje = new Ejecutivo(); eje.setNombre("Juan"); eje = ejecutivoRepository.save(eje);
        emp = new Empresa(); emp.setNombre("Photomat"); emp = empresaRepository.save(emp);
        t1 = nuevaTarea("1765");
        t2 = nuevaTarea("1802");
    }

    private Tarea nuevaTarea(String num) {
        Tarea t = new Tarea();
        t.setNumeroTarea(num);
        t.setFechaCreacion(LocalDate.now());
        return tareaRepository.save(t);
    }

    private void asignar(Cliente cli, String trimestre, Tarea tarea, Integer bandeja, Integer slot) {
        Dosimetro d = new Dosimetro();
        d.setNumero(numeroSeq++);
        d.setTipoDosimetro(tld);
        d.setTipoPorta(porta);
        d.setTarea(tarea);
        d.setNumeroBandeja(bandeja);
        d.setSlotBandeja(slot);
        d.setEstado("asignado");
        d.setFechaCreacion(LocalDate.now());
        d = dosimetroRepository.save(d);

        Asignacion a = new Asignacion();
        a.setDosimetro(d);
        a.setCliente(cli);
        a.setEjecutivo(eje);
        a.setEmpresa(emp);
        a.setTipoPorta(porta);
        a.setTarea(tarea);
        a.setNumeroBandeja(bandeja);
        a.setSlotBandeja(slot);
        a.setTrimestre(trimestre);
        a.setFechaAsignacion(LocalDate.now());
        asignacionRepository.save(a);
    }

    @Test
    void filtraPorClienteTrimestreYTarea() {
        asignar(c1, "2T2026", t1, 1, 10);
        asignar(c1, "2T2026", t2, 1, 10);   // otra tarea
        asignar(c2, "2T2026", t1, 1, 10);   // otro cliente
        asignar(c1, "1T2026", t1, 1, 10);   // otro trimestre

        // Sin tarea: cliente1 + 2T2026 => las dos (t1 y t2).
        assertEquals(2, asignacionRepository.paraLiberar(
                c1.getId(), "2T2026", null, null, null, null, null).size());
        // Con tarea t1 (por número): solo una.
        List<Asignacion> soloT1 = asignacionRepository.paraLiberar(
                c1.getId(), "2T2026", t1.getNumeroTarea(), null, null, null, null);
        assertEquals(1, soloT1.size());
        assertEquals(t1.getId(), soloT1.get(0).getTarea().getId());
    }

    @Test
    void rangoLexicograficoDeBandejaYSlot() {
        // Mismo cliente/trimestre/tarea, distintas (bandeja, slot).
        asignar(c1, "2T2026", t1, 1, 10);
        asignar(c1, "2T2026", t1, 1, 20);
        asignar(c1, "2T2026", t1, 2, 5);
        asignar(c1, "2T2026", t1, 2, 40);
        asignar(c1, "2T2026", t1, 3, 1);

        // Rango [ (1,20) .. (2,40) ] => (1,20), (2,5), (2,40) = 3.
        List<Asignacion> r = asignacionRepository.paraLiberar(
                c1.getId(), "2T2026", t1.getNumeroTarea(), 1, 20, 2, 40);
        assertEquals(3, r.size());
        // No incluye (1,10) ni (3,1).
        assertTrue(r.stream().noneMatch(a -> a.getNumeroBandeja() == 1 && a.getSlotBandeja() == 10));
        assertTrue(r.stream().noneMatch(a -> a.getNumeroBandeja() == 3));
        // Orden ascendente por bandeja y slot.
        assertEquals(1, r.get(0).getNumeroBandeja());
        assertEquals(20, r.get(0).getSlotBandeja());
        assertEquals(2, r.get(2).getNumeroBandeja());
        assertEquals(40, r.get(2).getSlotBandeja());
    }
}
