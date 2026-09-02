package com.dosimetros.backend.informeisp;

import com.dosimetros.backend.informeisp.entity.IspClienteTecnologia;
import com.dosimetros.backend.informeisp.entity.IspCodigoServicio;
import com.dosimetros.backend.informeisp.entity.IspPersonaCodigo;
import com.dosimetros.backend.informeisp.model.FilaDosis;
import com.dosimetros.backend.informeisp.model.FilaInforme;
import com.dosimetros.backend.informeisp.model.ResultadoProceso;
import com.dosimetros.backend.informeisp.repository.IspClienteTecnologiaRepository;
import com.dosimetros.backend.informeisp.repository.IspCodigoServicioRepository;
import com.dosimetros.backend.informeisp.repository.IspPersonaCodigoRepository;
import com.dosimetros.backend.informeisp.service.AsignacionCodigosService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsignacionCodigosServiceTest {

    private final IspCodigoServicioRepository codServRepo = mock(IspCodigoServicioRepository.class);
    private final IspPersonaCodigoRepository personaRepo = mock(IspPersonaCodigoRepository.class);
    private final IspClienteTecnologiaRepository clienteTecRepo = mock(IspClienteTecnologiaRepository.class);
    private final AsignacionCodigosService service =
            new AsignacionCodigosService(codServRepo, personaRepo, clienteTecRepo);

    private IspCodigoServicio serv(String empresa, String tec, String mag, String per, int codigo) {
        IspCodigoServicio c = new IspCodigoServicio();
        c.setEmpresa(empresa);
        c.setTecnologia(tec);
        c.setMagnitud(mag);
        c.setPeriodicidad(per);
        c.setCodigo(codigo);
        return c;
    }

    private FilaInforme fila(int nfila, String cliente, String rutEnt, String rut, String usuario,
                             String genero, String tipo, String dosim, String ubic, String periodo,
                             String prof, String piel, String cris) {
        FilaInforme f = new FilaInforme();
        f.filaExcel = nfila;
        f.cliente = cliente;
        f.documentoCliente = rutEnt;
        f.rut = rut;
        f.usuario = usuario;
        f.genero = genero;
        f.tipoDosimetro = tipo;
        f.dosimetro = dosim;
        f.ubicacion = ubic;
        f.periodicidad = periodo;
        f.dosisProfundidad = prof;
        f.dosisPiel = piel;
        f.dosisCristalino = cris;
        return f;
    }

    @Test
    void asignaCodigosDesdeMaestrasYReglas() {
        when(codServRepo.findAll()).thenReturn(List.of(
                serv("Dosimet", "TLD", "HP10", "TRIMESTRAL", 3)));
        when(personaRepo.findAll()).thenReturn(List.of(
                new IspPersonaCodigo("Dosimet", "11111111-1", 10, 5)));
        when(clienteTecRepo.findAll()).thenReturn(List.of(
                new IspClienteTecnologia("Dosimet", "76458223-3", "TLD")));

        List<FilaInforme> filas = List.of(
                fila(2, "HOSPITAL X", "76458223-3", "11111111-1", "JUAN PEREZ", "Masculino",
                        "CUERPO COMPLETO/ PERSONAL", "AB123", "PERSONAL", "TRIMESTRAL",
                        "0.101", "-", "-"));

        ResultadoProceso res = service.procesar("Dosimet", filas);

        assertEquals(1, res.dosis.size());
        FilaDosis d = res.dosis.get(0);
        assertEquals(3, d.codServ);       // TLD/HP10/TRIMESTRAL en Dosimet
        assertEquals(10, d.codCargo);     // de la maestra
        assertEquals(5, d.codPrac);       // de la maestra
        assertEquals(0.10, d.dosis, 1e-9);
        assertNull(d.observa);
        assertEquals(1, res.personasUnicas);
        assertEquals(0, res.filasEliminadas);
    }

    @Test
    void eliminaFilasSinRutOSinDosimetro() {
        when(codServRepo.findAll()).thenReturn(List.of(serv("Dosimet", "TLD", "HP10", "TRIMESTRAL", 3)));
        when(personaRepo.findAll()).thenReturn(List.of());
        when(clienteTecRepo.findAll()).thenReturn(List.of());

        List<FilaInforme> filas = new ArrayList<>();
        // sin rut
        filas.add(fila(2, "C", "76000000-0", "", "", "", "DE REFERENCIA", "REF1", "REFERENCIA",
                "TRIMESTRAL", "0.4", "-", "-"));
        // persona sin dosímetro
        filas.add(fila(3, "C", "76000000-0", "22222222-2", "ANA", "Femenino",
                "CUERPO COMPLETO/ PERSONAL", "", "PERSONAL", "TRIMESTRAL", "MNR", "-", "-"));

        ResultadoProceso res = service.procesar("Dosimet", filas);

        assertEquals(0, res.dosis.size());
        assertEquals(2, res.filasEliminadas);
    }

    @Test
    void personaSinMatchSeSugierePorCompaneros() {
        when(codServRepo.findAll()).thenReturn(List.of(serv("Dosimet", "TLD", "HP10", "TRIMESTRAL", 3)));
        when(personaRepo.findAll()).thenReturn(List.of(
                new IspPersonaCodigo("Dosimet", "11111111-1", 13, 6)));
        when(clienteTecRepo.findAll()).thenReturn(List.of(
                new IspClienteTecnologia("Dosimet", "76458223-3", "TLD")));

        List<FilaInforme> filas = List.of(
                // conocida: match por (empresa + RUT) -> aporta la referencia
                fila(2, "CLINICA", "76458223-3", "11111111-1", "JUAN", "Masculino",
                        "CUERPO COMPLETO/ PERSONAL", "A1", "PERSONAL", "TRIMESTRAL", "0.1", "-", "-"),
                // sin match, mismo cliente+área -> se sugiere por el compañero
                fila(3, "CLINICA", "76458223-3", "99999999-9", "NUEVA", "Femenino",
                        "CUERPO COMPLETO/ PERSONAL", "A2", "PERSONAL", "TRIMESTRAL", "DND", "-", "-"));

        ResultadoProceso res = service.procesar("Dosimet", filas);

        assertEquals(2, res.dosis.size());
        FilaDosis conocida = res.dosis.stream().filter(x -> x.run.equals("11111111-1")).findFirst().orElseThrow();
        assertEquals(13, conocida.codCargo);
        assertEquals(6, conocida.codPrac);
        assertFalse(conocida.cargoSugerido);
        assertFalse(conocida.pracSugerido);

        FilaDosis nueva = res.dosis.stream().filter(x -> x.run.equals("99999999-9")).findFirst().orElseThrow();
        assertEquals(13, nueva.codCargo); // sugerido por compañero del mismo cliente+área
        assertEquals(6, nueva.codPrac);
        assertTrue(nueva.cargoSugerido);
        assertTrue(nueva.pracSugerido);
        assertEquals("NR", nueva.observa); // DND -> NR
        assertTrue(res.inconsistencias.stream().anyMatch(i -> i.tipo.equals("PERSONA_CODIGO_SUGERIDO")));
    }

    @Test
    void sinCompanerosEnLaMismaAreaQuedaEnBlanco() {
        when(codServRepo.findAll()).thenReturn(List.of(serv("Dosimet", "TLD", "HP10", "TRIMESTRAL", 3)));
        when(personaRepo.findAll()).thenReturn(List.of(
                new IspPersonaCodigo("Dosimet", "11111111-1", 13, 6)));
        when(clienteTecRepo.findAll()).thenReturn(List.of());

        // Conocida y nueva son del mismo cliente pero de ÁREAS distintas.
        FilaInforme conocida = fila(2, "CLINICA", "76458223-3", "11111111-1", "JUAN", "Masculino",
                "CUERPO COMPLETO/ PERSONAL", "A1", "PERSONAL", "TRIMESTRAL", "0.1", "-", "-");
        conocida.area = "RAYOS";
        FilaInforme nueva = fila(3, "CLINICA", "76458223-3", "99999999-9", "ANA", "Femenino",
                "CUERPO COMPLETO/ PERSONAL", "A2", "PERSONAL", "TRIMESTRAL", "0.2", "-", "-");
        nueva.area = "PABELLON"; // área distinta -> sin compañeros de referencia

        ResultadoProceso res = service.procesar("Dosimet", List.of(conocida, nueva));

        FilaDosis fd = res.dosis.stream().filter(x -> x.run.equals("99999999-9")).findFirst().orElseThrow();
        assertNull(fd.codCargo);
        assertNull(fd.codPrac);
        assertFalse(fd.cargoSugerido);
        assertFalse(fd.pracSugerido);
        assertTrue(res.inconsistencias.stream().anyMatch(i -> i.tipo.equals("PERSONA_SIN_CARGO_PRAC")));
    }

    @Test
    void tecnologiaDeColumnaMandaSobreDefaultYMaestra() {
        // Solo hay código OSL; sin columna caería a TLD (default Dosimet) y no habría COD SERV.
        when(codServRepo.findAll()).thenReturn(List.of(serv("Dosimet", "OSL", "HP10", "TRIMESTRAL", 50)));
        when(personaRepo.findAll()).thenReturn(List.of());
        when(clienteTecRepo.findAll()).thenReturn(List.of());

        FilaInforme f = fila(2, "HOSPITAL", "76000000-0", "11111111-1", "JUAN", "Masculino",
                "CUERPO COMPLETO/ PERSONAL", "A1", "PERSONAL", "TRIMESTRAL", "0.2", "-", "-");
        f.tecnologia = "OSL"; // indicada en el informe

        ResultadoProceso res = service.procesar("Dosimet", List.of(f));

        assertEquals(1, res.dosis.size());
        assertEquals(50, res.dosis.get(0).codServ); // usó OSL de la columna, no el default
    }

    @Test
    void maestraDePersonaEsPorEmpresa() {
        when(codServRepo.findAll()).thenReturn(List.of(serv("Dosimet", "TLD", "HP10", "TRIMESTRAL", 3)));
        // La persona existe, pero en OTRO laboratorio (Photomat): no debe matchear en Dosimet.
        when(personaRepo.findAll()).thenReturn(List.of(
                new IspPersonaCodigo("Photomat", "11111111-1", 10, 5)));
        when(clienteTecRepo.findAll()).thenReturn(List.of(
                new IspClienteTecnologia("Dosimet", "76000000-0", "TLD")));

        FilaInforme f = fila(2, "HOSPITAL", "76000000-0", "11111111-1", "JUAN", "Masculino",
                "CUERPO COMPLETO/ PERSONAL", "A1", "PERSONAL", "TRIMESTRAL", "0.1", "-", "-");

        ResultadoProceso res = service.procesar("Dosimet", List.of(f));

        assertEquals(1, res.dosis.size());
        assertNull(res.dosis.get(0).codCargo); // no matchea: es de otra empresa
        assertNull(res.dosis.get(0).codPrac);
        assertTrue(res.inconsistencias.stream().anyMatch(i -> i.tipo.equals("PERSONA_SIN_CARGO_PRAC")));
    }
}
