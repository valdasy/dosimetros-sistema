package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.dosimetro.ActualizarTipoPortaRangoResponse;
import com.dosimetros.backend.dto.dosimetro.DosimetroResponse;
import com.dosimetros.backend.dto.tarea.EliminarTareasResponse;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Tarea;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.TareaRepository;
import com.dosimetros.backend.repository.TipoDosimetroRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DosimetroServiceTest {

    private final DosimetroRepository dosimetroRepository = mock(DosimetroRepository.class);
    private final TipoPortaRepository tipoPortaRepository = mock(TipoPortaRepository.class);
    private final TareaRepository tareaRepository = mock(TareaRepository.class);
    private final AsignacionRepository asignacionRepository = mock(AsignacionRepository.class);
    private final DosimetroService service = new DosimetroService(
            dosimetroRepository,
            mock(TipoDosimetroRepository.class),
            tipoPortaRepository,
            tareaRepository,
            asignacionRepository);

    private Tarea tarea(int id, String numero) {
        Tarea t = new Tarea();
        t.setId(id);
        t.setNumeroTarea(numero);
        return t;
    }

    private TipoPorta porta(int id, int tipoDosimetroId) {
        TipoDosimetro td = new TipoDosimetro();
        td.setId(tipoDosimetroId);
        td.setNombre("TLD");
        TipoPorta tp = new TipoPorta();
        tp.setId(id);
        tp.setNombre("Gringo");
        tp.setTipoDosimetro(td);
        return tp;
    }

    private Dosimetro dosimetro(String estado) {
        TipoDosimetro tipo = new TipoDosimetro();
        tipo.setId(2);
        tipo.setNombre("TLD");
        Dosimetro d = new Dosimetro();
        d.setId(1);
        d.setNumero(1234);
        d.setTipoDosimetro(tipo);
        d.setEstado(estado);
        return d;
    }

    @Test
    void marcarDanadoDejaElDosimetroDanado() {
        Dosimetro d = dosimetro("disponible");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DosimetroResponse resp = service.marcarDanado(1);

        assertEquals("dañado", resp.getEstado());
    }

    @Test
    void noSePuedeDanarUnDosimetroAsignado() {
        Dosimetro d = dosimetro("asignado");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class, () -> service.marcarDanado(1));
        verify(dosimetroRepository, never()).save(any());
    }

    @Test
    void marcarBuenoVuelveADisponible() {
        Dosimetro d = dosimetro("dañado");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DosimetroResponse resp = service.marcarBueno(1);

        assertEquals("disponible", resp.getEstado());
    }

    @Test
    void marcarBuenoFallaSiNoEstabaDanado() {
        Dosimetro d = dosimetro("disponible");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class, () -> service.marcarBueno(1));
        verify(dosimetroRepository, never()).save(any());
    }

    @Test
    void armarSeleccionAsignaLaPortaALosDosimetros() {
        TipoPorta tp = porta(5, 2); // porta de tipo TLD (id 2)
        Dosimetro d = dosimetro("disponible"); // dosímetro TLD (id 2)
        when(tipoPortaRepository.findById(5)).thenReturn(Optional.of(tp));
        when(dosimetroRepository.findAllById(List.of(1))).thenReturn(List.of(d));
        when(dosimetroRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ActualizarTipoPortaRangoResponse resp = service.armarSeleccion(List.of(1), 5);

        assertEquals(1, resp.getDosimetrosActualizados());
        assertEquals(5, d.getTipoPorta().getId());
    }

    @Test
    void armarSeleccionFallaSiLaPortaNoEsCompatible() {
        TipoPorta tp = porta(5, 99); // porta de otro tipo de dosímetro
        Dosimetro d = dosimetro("disponible"); // dosímetro TLD (id 2)
        when(tipoPortaRepository.findById(5)).thenReturn(Optional.of(tp));
        when(dosimetroRepository.findAllById(List.of(1))).thenReturn(List.of(d));

        assertThrows(IllegalArgumentException.class, () -> service.armarSeleccion(List.of(1), 5));
        verify(dosimetroRepository, never()).saveAll(any());
    }

    @Test
    void marcarExtraviadoDejaElDosimetroExtraviado() {
        Dosimetro d = dosimetro("asignado");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DosimetroResponse resp = service.marcarExtraviado(1, "No encontrado");

        assertEquals("extraviado", resp.getEstado());
    }

    @Test
    void noSePuedeMarcarExtraviadoUnDadoDeBaja() {
        Dosimetro d = dosimetro("baja");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class, () -> service.marcarExtraviado(1, null));
        verify(dosimetroRepository, never()).save(any());
    }

    @Test
    void eliminarTareasDesarmaLosDosimetrosYBorraLaTarea() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        Dosimetro d1 = dosimetro("disponible"); d1.setTarea(tarea(7, "50")); d1.setNumeroBandeja(3); d1.setSlotBandeja(9);
        Dosimetro d2 = dosimetro("disponible"); d2.setId(2); d2.setTarea(tarea(7, "50"));
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(d1, d2));
        when(asignacionRepository.contarAsignacionesEnTarea(7)).thenReturn(0L);
        when(dosimetroRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        TipoPorta sinArmar = porta(99, 2); // porta "Sin armar" de la tecnología (id 2)
        when(tipoPortaRepository.findSinArmarByTipoDosimetro(2)).thenReturn(List.of(sinArmar));

        EliminarTareasResponse resp = service.eliminarTareas(List.of(7));

        assertEquals(1, resp.getTareas());
        assertEquals(2, resp.getDosimetros());
        // Los dosímetros se conservan pero quedan desarmados (sin tarea/bandeja/slot)
        // y con la porta "Sin armar".
        assertEquals(null, d1.getTarea());
        assertEquals(null, d1.getNumeroBandeja());
        assertEquals(null, d1.getSlotBandeja());
        assertEquals(99, d1.getTipoPorta().getId());
        assertEquals(null, d2.getTarea());
        assertEquals(99, d2.getTipoPorta().getId());
        verify(dosimetroRepository).saveAll(any());
        verify(dosimetroRepository, never()).deleteAll(any()); // NO se borran dosímetros
        verify(tareaRepository).deleteById(7);
    }

    @Test
    void eliminarTareasFallaSiTieneDosimetroAsignado() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(dosimetro("asignado")));
        when(asignacionRepository.contarAsignacionesEnTarea(7)).thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> service.eliminarTareas(List.of(7)));
        verify(tareaRepository, never()).deleteById(any());
        verify(dosimetroRepository, never()).saveAll(any());
    }

    @Test
    void eliminarTareasFallaSiSeAsignoEnLaTarea() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(dosimetro("disponible")));
        when(asignacionRepository.contarAsignacionesEnTarea(7)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> service.eliminarTareas(List.of(7)));
        verify(tareaRepository, never()).deleteById(any());
    }

    @Test
    void sacarDelRangoSacaSoloLosDisponiblesYLosDejaEnLimbo() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        Dosimetro disp = dosimetro("disponible");
        disp.setTarea(tarea(7, "50")); disp.setNumeroBandeja(2); disp.setSlotBandeja(5);
        Dosimetro asig = dosimetro("asignado"); asig.setId(2);
        asig.setTarea(tarea(7, "50")); asig.setNumeroBandeja(2); asig.setSlotBandeja(6);
        when(dosimetroRepository.findByTareaYRangoBandejaSlot(7, 2, 2, null, null))
                .thenReturn(List.of(disp, asig));
        TipoPorta sinArmar = porta(99, 2);
        when(tipoPortaRepository.findSinArmarByTipoDosimetro(2)).thenReturn(List.of(sinArmar));
        when(dosimetroRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        com.dosimetros.backend.dto.dosimetro.SacarRangoRequest req =
                new com.dosimetros.backend.dto.dosimetro.SacarRangoRequest();
        req.setTareaId(7); req.setBandejaDesde(2); req.setBandejaHasta(2);

        com.dosimetros.backend.dto.dosimetro.SacarRangoResponse resp = service.sacarDelRango(req);

        assertEquals(1, resp.getSacados());
        assertEquals(1, resp.getOmitidos());
        // El disponible queda en limbo (sin tarea/bandeja/slot, porta "Sin armar").
        assertEquals(null, disp.getTarea());
        assertEquals(null, disp.getNumeroBandeja());
        assertEquals(null, disp.getSlotBandeja());
        assertEquals(99, disp.getTipoPorta().getId());
        assertEquals("disponible", disp.getEstado());
        // El asignado no se toca.
        assertEquals(7, asig.getTarea().getId());
        assertEquals("asignado", asig.getEstado());
    }

    @Test
    void sacarDelRangoFallaSiBandejaDesdeMayorQueHasta() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        com.dosimetros.backend.dto.dosimetro.SacarRangoRequest req =
                new com.dosimetros.backend.dto.dosimetro.SacarRangoRequest();
        req.setTareaId(7); req.setBandejaDesde(5); req.setBandejaHasta(2);

        assertThrows(IllegalArgumentException.class, () -> service.sacarDelRango(req));
        verify(dosimetroRepository, never()).saveAll(any());
    }

    @Test
    void liberarBorraLaAsignacionVigenteYDejaDisponible() {
        Dosimetro d = dosimetro("asignado");
        com.dosimetros.backend.entity.Asignacion vigente = new com.dosimetros.backend.entity.Asignacion();
        vigente.setId(99);
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));
        when(asignacionRepository.findTopByDosimetroIdOrderByIdDesc(1)).thenReturn(Optional.of(vigente));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.liberar(1);

        verify(asignacionRepository).delete(vigente); // se borra la asignación vigente
        assertEquals("disponible", d.getEstado());
    }

    @Test
    void liberarNoBorraAsignacionSiNoEstabaAsignado() {
        Dosimetro d = dosimetro("disponible");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));
        when(dosimetroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.liberar(1);

        verify(asignacionRepository, never()).delete(any());
        assertEquals("disponible", d.getEstado());
    }

    @Test
    void noSePuedeLiberarUnDadoDeBaja() {
        Dosimetro d = dosimetro("baja");
        when(dosimetroRepository.findById(1)).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class, () -> service.liberar(1));
        verify(asignacionRepository, never()).delete(any());
        verify(dosimetroRepository, never()).save(any());
    }
}
