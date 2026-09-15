package com.dosimetros.backend.comparador.service;

import com.dosimetros.backend.comparador.model.UserRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cubre el bug reportado: con SEDE en blanco (software y cliente), un usuario
 * de "OFICINA SAG LOS ANDES" se emparejaba con personas de "OFICINA SAG
 * COQUIMBO" y "OFICINA SAG PUNTA ARENAS" porque Jaro-Winkler infla el score de
 * similitud cuando dos strings distintos comparten un prefijo largo
 * ("OFICINA SAG "), quedando por encima del umbral aunque las sedes/áreas son
 * completamente distintas.
 */
class ComparatorServiceTest {

    private final ComparatorService service = new ComparatorService(new NormalizationService());

    @Test
    void noDebeCruzarAreasDistintasQueComparenUnPrefijoLargo() {
        UserRecord sw = UserRecord.builder()
                .sede("")
                .area("OFICINA SAG LOS ANDES")
                .usuario("JOSE ARIAS URTUBIA")
                .rut("21282548-4")
                .ubicacion("PERSONAL")
                .build();

        UserRecord clienteOtraSede = UserRecord.builder()
                .rutCliente("15053481-K")
                .nombreCompletoCliente("JOSE AQUEA DIAZ")
                .areaCliente("OFICINA SAG COQUIMBO")
                .sedeCliente("")
                .ubicacionCliente("PERSONAL")
                .build();

        List<UserRecord> resultado = service.compare(List.of(sw), List.of(clienteOtraSede));

        UserRecord swResultado = resultado.stream()
                .filter(r -> "JOSE ARIAS URTUBIA".equals(r.getUsuario()))
                .findFirst().orElseThrow();

        // No debe quedar emparejado (ni MANTENER ni VERIFICAR) con alguien de otra área.
        assertEquals(UserRecord.ComparisonResult.QUITAR, swResultado.getResultado());
    }
}
