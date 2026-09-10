package com.dosimetros.backend.chilexpress;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.model.ArchivoChilexpress;
import com.dosimetros.backend.chilexpress.model.FilaChilexpress;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import com.dosimetros.backend.chilexpress.service.ChilexpressImportService;
import com.dosimetros.backend.chilexpress.service.ChilexpressReaderService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChilexpressImportServiceTest {

    private final ChilexpressOtRepository repo = mock(ChilexpressOtRepository.class);
    private final ChilexpressReaderService reader = mock(ChilexpressReaderService.class);
    private final ChilexpressImportService service = new ChilexpressImportService(repo, reader);

    private final MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

    private FilaChilexpress fila(String ot, String estado) {
        FilaChilexpress f = new FilaChilexpress();
        f.nroOt = ot;
        f.estado = estado;
        f.nombreDestinatario = "DEST " + ot;
        return f;
    }

    private ArchivoChilexpress arch(LocalDate desde, LocalDate hasta, FilaChilexpress... filas) {
        ArchivoChilexpress a = new ArchivoChilexpress();
        a.periodoDesde = desde;
        a.periodoHasta = hasta;
        a.filas = List.of(filas);
        return a;
    }

    private ChilexpressOt existente(String ot, String estado, LocalDate periodoHasta, LocalDateTime creado) {
        ChilexpressOt o = new ChilexpressOt();
        o.setId(1);
        o.setEmpresa("Dosimet");
        o.setNroOt(ot);
        o.setEstado(estado);
        o.setPeriodoHasta(periodoHasta);
        o.setCreadoEn(creado);
        o.setActualizadoEn(creado);
        return o;
    }

    @Test
    void primeraCargaRegistraTodasComoNuevas() throws Exception {
        when(reader.leer(any())).thenReturn(arch(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                fila("111", "EN PRE-RECEPCION"), fila("222", "ENTREGADO")));
        when(repo.findByEmpresaAndNroOtOrderByCreadoEnDesc(any(), any())).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Integer> r = service.importar("Dosimet", file);

        assertEquals(2, r.get("nuevas"));
        assertEquals(0, r.get("actualizadas"));
        assertEquals(2, r.get("total"));
        verify(repo, times(2)).save(any());
    }

    @Test
    void otVistaRecienteSeActualiza() throws Exception {
        ChilexpressOt prev = existente("111", "EN PRE-RECEPCION",
                LocalDate.of(2026, 8, 31), LocalDateTime.of(2026, 8, 20, 0, 0));
        // Nuevo periodo arranca 5 días después de la última vez visto -> misma OT.
        when(reader.leer(any())).thenReturn(arch(
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 30), fila("111", "ENTREGADO")));
        when(repo.findByEmpresaAndNroOtOrderByCreadoEnDesc(eq("Dosimet"), eq("111")))
                .thenReturn(List.of(prev));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Integer> r = service.importar("Dosimet", file);

        assertEquals(0, r.get("nuevas"));
        assertEquals(1, r.get("actualizadas"));
        assertEquals("ENTREGADO", prev.getEstado()); // se actualizó el estado
    }

    @Test
    void otReutilizadaTrasGapLargoSeRegistraComoNueva() throws Exception {
        ChilexpressOt viejo = existente("111", "ENTREGADO",
                LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 1, 0, 0));
        // Reaparece 7 meses después (gap >> 100 días) -> es otra encomienda.
        when(reader.leer(any())).thenReturn(arch(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), fila("111", "EN PRE-RECEPCION")));
        when(repo.findByEmpresaAndNroOtOrderByCreadoEnDesc(eq("Dosimet"), eq("111")))
                .thenReturn(List.of(viejo));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Integer> r = service.importar("Dosimet", file);

        assertEquals(1, r.get("nuevas"));
        assertEquals(0, r.get("actualizadas"));
        assertEquals("ENTREGADO", viejo.getEstado()); // el histórico NO se tocó
    }
}
