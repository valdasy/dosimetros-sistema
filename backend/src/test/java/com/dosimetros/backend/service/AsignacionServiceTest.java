package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.asignacion.AsignacionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.AsignacionMasivaResponse;
import com.dosimetros.backend.dto.asignacion.AsignacionRequest;
import com.dosimetros.backend.dto.asignacion.CorreccionMasivaRequest;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.entity.Empresa;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.ClienteRepository;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.EjecutivoRepository;
import com.dosimetros.backend.repository.EmpresaRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsignacionServiceTest {

    private final AsignacionRepository asignacionRepository = mock(AsignacionRepository.class);
    private final DosimetroRepository dosimetroRepository = mock(DosimetroRepository.class);
    private final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    private final EjecutivoRepository ejecutivoRepository = mock(EjecutivoRepository.class);
    private final EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
    private final TipoPortaRepository tipoPortaRepository = mock(TipoPortaRepository.class);

    private final AsignacionService service = new AsignacionService(
            asignacionRepository, dosimetroRepository, clienteRepository,
            ejecutivoRepository, empresaRepository, tipoPortaRepository);

    private TipoDosimetro tipoDosimetro(int id) {
        TipoDosimetro t = new TipoDosimetro();
        t.setId(id);
        t.setNombre("TLD");
        return t;
    }

    private TipoPorta tipoPorta(int id, int tipoDosimetroId) {
        TipoPorta p = new TipoPorta();
        p.setId(id);
        p.setNombre("Porta gringo");
        p.setTipoDosimetro(tipoDosimetro(tipoDosimetroId));
        return p;
    }

    private Dosimetro dosimetro(int id, String estado, int tipoDosimetroId) {
        Dosimetro d = new Dosimetro();
        d.setId(id);
        d.setNumero(1000 + id);
        d.setEstado(estado);
        d.setTipoDosimetro(tipoDosimetro(tipoDosimetroId));
        return d;
    }

    private void mockCatalogosMasivo() {
        Cliente c = new Cliente(); c.setId(1); c.setRazonSocial("Cliente X");
        Ejecutivo e = new Ejecutivo(); e.setId(1); e.setNombre("Juan");
        Empresa emp = new Empresa(); emp.setId(1); emp.setNombre("Photomat");
        when(clienteRepository.findById(1)).thenReturn(Optional.of(c));
        when(ejecutivoRepository.findById(1)).thenReturn(Optional.of(e));
        when(empresaRepository.findById(1)).thenReturn(Optional.of(emp));
        when(tipoPortaRepository.findById(3)).thenReturn(Optional.of(tipoPorta(3, 2)));
    }

    private AsignacionMasivaRequest requestMasivo(int cantidad) {
        AsignacionMasivaRequest r = new AsignacionMasivaRequest();
        r.setClienteId(1);
        r.setEjecutivoId(1);
        r.setEmpresaId(1);
        r.setTipoPortaId(3);
        r.setTrimestre("2T2026");
        r.setCantidad(cantidad);
        r.setTareaIds(List.of(1, 2));
        return r;
    }

    @Test
    void asignacionMasivaExitosa() {
        mockCatalogosMasivo();
        when(dosimetroRepository.findDisponiblesCompatiblesEnTareas(any(), eq(2)))
                .thenReturn(List.of(
                        dosimetro(10, "disponible", 2),
                        dosimetro(11, "disponible", 2),
                        dosimetro(12, "disponible", 2)));
        when(asignacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AsignacionMasivaResponse resp = service.asignarMasivo(requestMasivo(2));

        assertEquals(2, resp.getCantidadSolicitada());
        assertEquals(2, resp.getCantidadAsignada());
        verify(asignacionRepository, times(2)).save(any());
    }

    @Test
    void asignacionMasivaFallaPorStockInsuficiente() {
        mockCatalogosMasivo();
        when(dosimetroRepository.findDisponiblesCompatiblesEnTareas(any(), eq(2)))
                .thenReturn(List.of(dosimetro(10, "disponible", 2))); // solo 1 disponible

        assertThrows(IllegalArgumentException.class, () -> service.asignarMasivo(requestMasivo(5)));
        verify(asignacionRepository, never()).save(any());
    }

    @Test
    void crearFallaSiPortaNoEsCompatible() {
        // dosímetro TLD (tipo 2) con porta de tipo 3 -> incompatible
        when(dosimetroRepository.findByIdParaAsignar(1)).thenReturn(Optional.of(dosimetro(1, "disponible", 2)));
        Cliente c = new Cliente(); c.setId(1);
        Ejecutivo e = new Ejecutivo(); e.setId(1);
        Empresa emp = new Empresa(); emp.setId(1);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(c));
        when(ejecutivoRepository.findById(1)).thenReturn(Optional.of(e));
        when(empresaRepository.findById(1)).thenReturn(Optional.of(emp));
        when(tipoPortaRepository.findById(9)).thenReturn(Optional.of(tipoPorta(9, 3)));

        AsignacionRequest r = new AsignacionRequest();
        r.setDosimetroId(1);
        r.setClienteId(1);
        r.setEjecutivoId(1);
        r.setEmpresaId(1);
        r.setTipoPortaId(9);
        r.setTrimestre("2T2026");

        assertThrows(IllegalArgumentException.class, () -> service.crear(r));
        verify(asignacionRepository, never()).save(any());
    }

    @Test
    void crearFallaSiElDosimetroNoEstaDisponible() {
        when(dosimetroRepository.findByIdParaAsignar(1)).thenReturn(Optional.of(dosimetro(1, "asignado", 2)));
        Cliente c = new Cliente(); c.setId(1);
        Ejecutivo e = new Ejecutivo(); e.setId(1);
        Empresa emp = new Empresa(); emp.setId(1);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(c));
        when(ejecutivoRepository.findById(1)).thenReturn(Optional.of(e));
        when(empresaRepository.findById(1)).thenReturn(Optional.of(emp));
        when(tipoPortaRepository.findById(3)).thenReturn(Optional.of(tipoPorta(3, 2)));

        AsignacionRequest r = new AsignacionRequest();
        r.setDosimetroId(1);
        r.setClienteId(1);
        r.setEjecutivoId(1);
        r.setEmpresaId(1);
        r.setTipoPortaId(3);
        r.setTrimestre("2T2026");

        assertThrows(IllegalArgumentException.class, () -> service.crear(r));
        verify(asignacionRepository, never()).save(any());
    }

    private com.dosimetros.backend.entity.Asignacion asignacion(int id, int tipoDosimetroId) {
        com.dosimetros.backend.entity.Asignacion a = new com.dosimetros.backend.entity.Asignacion();
        a.setId(id);
        a.setDosimetro(dosimetro(id, "asignado", tipoDosimetroId));
        a.setLinkTrello("http://viejo");
        return a;
    }

    @Test
    void marcarEnvioMarcaLasAsignacionesComoEnviadas() {
        var a1 = asignacion(1, 2);
        var a2 = asignacion(2, 2);
        when(asignacionRepository.findAllById(List.of(1, 2))).thenReturn(List.of(a1, a2));
        when(asignacionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        int n = service.marcarEnvio(List.of(1, 2), true);

        assertEquals(2, n);
        assertTrue(a1.isEnviado());
        assertTrue(a2.isEnviado());
        assertEquals(java.time.LocalDate.now(), a1.getFechaEnvio());
    }

    @Test
    void marcarEnvioPuedeRevertir() {
        var a1 = asignacion(1, 2);
        a1.setEnviado(true);
        a1.setFechaEnvio(java.time.LocalDate.now());
        when(asignacionRepository.findAllById(List.of(1))).thenReturn(List.of(a1));
        when(asignacionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.marcarEnvio(List.of(1), false);

        assertEquals(false, a1.isEnviado());
        assertEquals(null, a1.getFechaEnvio());
    }

    @Test
    void correccionMasivaCambiaElLinkEnTodas() {
        var a1 = asignacion(1, 2);
        var a2 = asignacion(2, 2);
        when(asignacionRepository.findAllById(List.of(1, 2))).thenReturn(List.of(a1, a2));
        when(asignacionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new CorreccionMasivaRequest();
        req.setAsignacionIds(List.of(1, 2));
        req.setCampo("linkTrello");
        req.setValor("http://nuevo");

        int n = service.correccionMasiva(req);

        assertEquals(2, n);
        assertEquals("http://nuevo", a1.getLinkTrello());
        assertEquals("http://nuevo", a2.getLinkTrello());
    }

    @Test
    void correccionMasivaDePortaFallaSiEsIncompatible() {
        var a1 = asignacion(1, 2); // dosímetro tipo 2
        when(asignacionRepository.findAllById(List.of(1))).thenReturn(List.of(a1));
        when(tipoPortaRepository.findById(9)).thenReturn(Optional.of(tipoPorta(9, 3))); // porta tipo 3

        var req = new CorreccionMasivaRequest();
        req.setAsignacionIds(List.of(1));
        req.setCampo("tipoPortaId");
        req.setValor("9");

        assertThrows(IllegalArgumentException.class, () -> service.correccionMasiva(req));
        verify(asignacionRepository, never()).saveAll(any());
    }
}
