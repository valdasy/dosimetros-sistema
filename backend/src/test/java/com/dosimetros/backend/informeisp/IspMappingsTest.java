package com.dosimetros.backend.informeisp;

import com.dosimetros.backend.informeisp.service.IspMappings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IspMappingsTest {

    @Test
    void magnitudSegunUbicacion() {
        assertEquals("HP10", IspMappings.magnitudDeUbicacion("PERSONAL"));
        assertEquals("HP10", IspMappings.magnitudDeUbicacion("ABDOMINAL"));
        assertEquals("HP0.07", IspMappings.magnitudDeUbicacion("ANILLO/ PULSERA"));
        assertEquals("HP0.07", IspMappings.magnitudDeUbicacion("MUÑECA"));
        assertEquals("HP3", IspMappings.magnitudDeUbicacion("CRISTALINO"));
        assertEquals("HP3", IspMappings.magnitudDeUbicacion("TIROIDEO"));
        assertNull(IspMappings.magnitudDeUbicacion("REFERENCIA"));
    }

    @Test
    void localizacionSegunUbicacion() {
        assertEquals(1, IspMappings.localizacionDeUbicacion("PERSONAL"));
        assertEquals(2, IspMappings.localizacionDeUbicacion("DEDO"));
        assertEquals(3, IspMappings.localizacionDeUbicacion("MUÑECA"));
        assertEquals(4, IspMappings.localizacionDeUbicacion("CRISTALINO"));
    }

    @Test
    void interpretarDosisNumerica() {
        IspMappings.DosisIsp d = IspMappings.interpretarDosis("0.101");
        assertEquals(0.10, d.dosis, 1e-9);
        assertNull(d.observa);
    }

    @Test
    void interpretarDosisNumericaConComa() {
        IspMappings.DosisIsp d = IspMappings.interpretarDosis("0,4");
        assertEquals(0.40, d.dosis, 1e-9);
        assertNull(d.observa);
    }

    @Test
    void interpretarSiglas() {
        assertEquals("<LD", IspMappings.interpretarDosis("MNR").observa);
        assertEquals("NU", IspMappings.interpretarDosis("DSU").observa);
        assertEquals("NR", IspMappings.interpretarDosis("DND").observa);
        assertEquals("NR", IspMappings.interpretarDosis("DD").observa);
        assertEquals("NR", IspMappings.interpretarDosis("DE").observa);
        assertEquals("NR", IspMappings.interpretarDosis("NR").observa);
        // en todas las siglas la dosis va en 0.00
        assertEquals(0.0, IspMappings.interpretarDosis("MNR").dosis, 1e-9);
    }

    @Test
    void periodicidadBimestralMapeaBimensual() {
        assertEquals("BIMENSUAL", IspMappings.periodicidadCodigo("BIMESTRAL"));
        assertEquals("TRIMESTRAL", IspMappings.periodicidadCodigo("Trimestral"));
        assertEquals("MENSUAL", IspMappings.periodicidadCodigo("mensual"));
    }

    @Test
    void sexoDesdeGenero() {
        assertEquals("M", IspMappings.sexoIsp("Masculino"));
        assertEquals("F", IspMappings.sexoIsp("Femenino"));
        assertEquals("", IspMappings.sexoIsp(""));
    }
}
