package com.dosimetros.backend.chilexpress;

import com.dosimetros.backend.chilexpress.controller.ChilexpressController;
import com.dosimetros.backend.chilexpress.dto.PanelChilexpressResponse;
import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import com.dosimetros.backend.chilexpress.service.ChilexpressImportService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChilexpressControllerTest {

    private final ChilexpressImportService importService = mock(ChilexpressImportService.class);
    private final ChilexpressOtRepository repo = mock(ChilexpressOtRepository.class);
    private final ChilexpressController controller = new ChilexpressController(importService, repo);

    private ChilexpressOt ot(int id, String estado) {
        ChilexpressOt o = new ChilexpressOt();
        o.setId(id);
        o.setEmpresa("Dosimet");
        o.setNroOt(String.valueOf(id));
        o.setEstado(estado);
        return o;
    }

    @Test
    void panelExcluyePreRecepcionDePendientes() {
        when(repo.retiroEnSucursal()).thenReturn(List.of());
        when(repo.conProblema()).thenReturn(List.of());
        when(repo.findByFechaEntregaIsNullOrderByActualizadoEnDesc()).thenReturn(List.of(
                ot(1, "EN CONTENEDOR"),      // en viaje -> pendiente
                ot(2, "EN PRE-RECEPCION")));  // creada, no enviada -> se ignora

        PanelChilexpressResponse panel = controller.panel().getBody();

        assertNotNull(panel);
        assertEquals(1, panel.getPendientes().size());
        assertEquals("1", panel.getPendientes().get(0).getNroOt());
        assertTrue(panel.getPendientes().stream().noneMatch(o -> o.getNroOt().equals("2")));
    }

    @Test
    void buscarSinFiltrosDevuelveVacio() {
        var resp = controller.buscar(null, null, null, "entrega", null, null);
        assertEquals(List.of(), resp.getBody());
        verify(repo, never()).buscar(any(), any(), anyBoolean(), any(), any(), any(), any());
    }
}
