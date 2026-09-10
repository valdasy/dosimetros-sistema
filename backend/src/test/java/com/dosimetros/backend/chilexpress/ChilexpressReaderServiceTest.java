package com.dosimetros.backend.chilexpress;

import com.dosimetros.backend.chilexpress.model.ArchivoChilexpress;
import com.dosimetros.backend.chilexpress.model.FilaChilexpress;
import com.dosimetros.backend.chilexpress.service.ChilexpressReaderService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ChilexpressReaderServiceTest {

    private final ChilexpressReaderService reader = new ChilexpressReaderService();

    private MockMultipartFile fixture() throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/chilexpress_sample.xls").readAllBytes();
        return new MockMultipartFile("file", "consulta.xls", "application/vnd.ms-excel", bytes);
    }

    @Test
    void leeFilasEncabezadosPorNombreYPeriodo() throws Exception {
        ArchivoChilexpress arch = reader.leer(fixture());

        assertEquals(2, arch.filas.size());
        assertEquals(LocalDate.of(2026, 8, 1), arch.periodoDesde);
        assertEquals(LocalDate.of(2026, 8, 31), arch.periodoHasta);

        FilaChilexpress a = arch.filas.get(0);
        assertEquals("111111111111", a.nroOt);
        assertEquals("CLIENTE UNO", a.nroReferencia);
        assertEquals("HOSPITAL UNO", a.nombreDestinatario);
        assertEquals("ARICA", a.destino);
        assertEquals("ENTREGADO", a.estado);
        assertEquals(100000, a.valorDeclarado);
        assertEquals("Juan Perez", a.receptor);
        assertEquals(LocalDate.of(2026, 8, 5), a.fechaEntrega);
        assertEquals("10:30", a.horaEntrega);
        assertTrue(a.certificadoEntrega.contains("chilexpress.cl")); // toma el href, no "Ver"

        FilaChilexpress b = arch.filas.get(1);
        assertEquals("222222222222", b.nroOt);
        assertEquals("EN PRE-RECEPCION", b.estado);
        assertNull(b.fechaEntrega); // aún no entregado
    }
}
