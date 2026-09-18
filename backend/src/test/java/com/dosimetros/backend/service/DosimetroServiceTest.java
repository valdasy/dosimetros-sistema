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
    void eliminarTareasBorraTareaYSusDosimetros() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        Dosimetro d1 = dosimetro("disponible");
        Dosimetro d2 = dosimetro("disponible"); d2.setId(2);
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(d1, d2));
        when(asignacionRepository.contarAsignacionesDeTarea(7)).thenReturn(0L);

        EliminarTareasResponse resp = service.eliminarTareas(List.of(7));

        assertEquals(1, resp.getTareas());
        assertEquals(2, resp.getDosimetros());
        verify(dosimetroRepository).deleteAll(any());
        verify(tareaRepository).deleteById(7);
    }

    @Test
    void eliminarTareasFallaSiTieneDosimetroNoDisponible() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(dosimetro("asignado")));
        when(asignacionRepository.contarAsignacionesDeTarea(7)).thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> service.eliminarTareas(List.of(7)));
        verify(tareaRepository, never()).deleteById(any());
        verify(dosimetroRepository, never()).deleteAll(any());
    }

    @Test
    void eliminarTareasFallaSiTieneHistorialDeAsignacion() {
        when(tareaRepository.findById(7)).thenReturn(Optional.of(tarea(7, "50")));
        when(dosimetroRepository.findByTareaId(7)).thenReturn(List.of(dosimetro("disponible")));
        when(asignacionRepository.contarAsignacionesDeTarea(7)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> service.eliminarTareas(List.of(7)));
        verify(tareaRepository, never()).deleteById(any());
    }
}
