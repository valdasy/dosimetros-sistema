package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.asignacion.ImportacionAsignacionesResponse;
import com.dosimetros.backend.entity.Asignacion;
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
import com.dosimetros.backend.repository.TipoDosimetroRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifica el upsert de asignaciones por archivo (#12): crear/actualizar/sin cambios. */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ImportacionAsignacionServiceTest {

    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private DosimetroRepository dosimetroRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private EjecutivoRepository ejecutivoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private TipoDosimetroRepository tipoDosimetroRepository;
    @Autowired private TipoPortaRepository tipoPortaRepository;

    private ImportacionAsignacionService service;
    private TipoPorta gringo;
    private Cliente acme;
    private Ejecutivo juan;
    private Empresa marca;

    @BeforeEach
    void setUp() {
        service = new ImportacionAsignacionService(asignacionRepository, dosimetroRepository,
                clienteRepository, ejecutivoRepository, empresaRepository, tipoPortaRepository);

        TipoDosimetro tld = new TipoDosimetro();
        tld.setNombre("TLD");
        tld = tipoDosimetroRepository.save(tld);
        gringo = new TipoPorta();
        gringo.setNombre("Porta gringo");
        gringo.setTipoDosimetro(tld);
        gringo = tipoPortaRepository.save(gringo);

        acme = new Cliente();
        acme.setRazonSocial("ACME");
        acme.setActivo(true);
        acme = clienteRepository.save(acme);

        juan = new Ejecutivo();
        juan.setNombre("Juan");
        juan.setActivo(true);
        juan = ejecutivoRepository.save(juan);

        marca = new Empresa();
        marca.setNombre("MarcaA");
        marca.setActiva(true);
        marca = empresaRepository.save(marca);

        // 100: disponible sin asignación -> se creará
        seedDosimetro(100, tld, "disponible");
        // 200: ya asignado idéntico a la fila -> sin cambios
        Dosimetro d200 = seedDosimetro(200, tld, "asignado");
        seedAsignacion(d200, "2T2025", "http://a");
        // 300: asignado con link distinto -> se actualiza
        Dosimetro d300 = seedDosimetro(300, tld, "asignado");
        seedAsignacion(d300, "2T2025", "http://old");
    }

    private Dosimetro seedDosimetro(int numero, TipoDosimetro tld, String estado) {
        Dosimetro d = new Dosimetro();
        d.setNumero(numero);
        d.setTipoDosimetro(tld);
        d.setTipoPorta(gringo);
        d.setEstado(estado);
        d.setFechaCreacion(LocalDate.of(2025, 1, 1));
        return dosimetroRepository.save(d);
    }

    private void seedAsignacion(Dosimetro d, String trimestre, String link) {
        Asignacion a = new Asignacion();
        a.setDosimetro(d);
        a.setCliente(acme);
        a.setEjecutivo(juan);
        a.setEmpresa(marca);
        a.setTipoPorta(gringo);
        a.setTrimestre(trimestre);
        a.setFechaAsignacion(LocalDate.of(2025, 4, 1));
        a.setLinkTrello(link);
        asignacionRepository.save(a);
    }

    private MockMultipartFile excel(String[][] filas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Datos");
            Row header = sheet.createRow(0);
            String[] cols = {"numero", "cliente", "ejecutivo", "empresa", "porta", "trimestre", "link"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < filas[r].length; c++) row.createCell(c).setCellValue(filas[r][c]);
            }
            wb.write(out);
            return new MockMultipartFile("file", "asignaciones.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void upsertCreaActualizaYDetectaSinCambios() {
        ImportacionAsignacionesResponse resp = service.importarExcel(excel(new String[][]{
                {"100", "ACME", "Juan", "MarcaA", "Porta gringo", "2T2025", "http://a"}, // crea
                {"200", "ACME", "Juan", "MarcaA", "Porta gringo", "2T2025", "http://a"}, // sin cambios
                {"300", "ACME", "Juan", "MarcaA", "Porta gringo", "2T2025", "http://new"}, // actualiza
        }));

        assertEquals(3, resp.getTotalFilas());
        assertEquals(1, resp.getCreados());
        assertEquals(1, resp.getSinCambios());
        assertEquals(1, resp.getActualizados());
        assertEquals(0, resp.getFallidas());

        // El dosímetro 100 quedó asignado y con asignación.
        Dosimetro d100 = dosimetroRepository.findByNumeroOrderByIdAsc(100).get(0);
        assertEquals("asignado", d100.getEstado());
        assertEquals(1, asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(d100.getId()).size());

        // El 300 actualizó su link.
        Dosimetro d300 = dosimetroRepository.findByNumeroOrderByIdAsc(300).get(0);
        assertEquals("http://new",
                asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(d300.getId()).get(0).getLinkTrello());
    }

    @Test
    void importarOtroTrimestreNoBorraLaAsignacionDelTrimestreAnterior() {
        // Crítico #4: el 200 ya tiene una asignación en 2T2025. Al importar el
        // archivo del 3T2025 debe CREARSE un registro nuevo para ese trimestre y
        // CONSERVARSE el de 2T2025 (historial de exposición), no sobrescribirlo.
        Dosimetro d200 = dosimetroRepository.findByNumeroOrderByIdAsc(200).get(0);
        Integer id200 = d200.getId();
        assertEquals(1, asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(id200).size());

        ImportacionAsignacionesResponse resp = service.importarExcel(excel(new String[][]{
                {"200", "ACME", "Juan", "MarcaA", "Porta gringo", "3T2025", "http://a"},
        }));

        assertEquals(1, resp.getCreados());
        assertEquals(0, resp.getActualizados());
        assertEquals(0, resp.getSinCambios());

        // Ahora hay DOS asignaciones: 2T2025 (preservada) y 3T2025 (nueva).
        var todas = asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(id200);
        assertEquals(2, todas.size());
        assertEquals(1, todas.stream().filter(a -> "2T2025".equals(a.getTrimestre())).count());
        assertEquals(1, todas.stream().filter(a -> "3T2025".equals(a.getTrimestre())).count());
    }

    @Test
    void importarMismoTrimestreActualizaEnLugarDeDuplicar() {
        // Contraparte del anterior: reimportar el MISMO trimestre con un cambio
        // actualiza el registro existente, sin crear duplicados.
        Dosimetro d300 = dosimetroRepository.findByNumeroOrderByIdAsc(300).get(0);
        Integer id300 = d300.getId();

        ImportacionAsignacionesResponse resp = service.importarExcel(excel(new String[][]{
                {"300", "ACME", "Juan", "MarcaA", "Porta gringo", "2T2025", "http://actualizado"},
        }));

        assertEquals(0, resp.getCreados());
        assertEquals(1, resp.getActualizados());
        var todas = asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(id300);
        assertEquals(1, todas.size());
        assertEquals("http://actualizado", todas.get(0).getLinkTrello());
    }

    @Test
    void upsertReportaErrorSiElDosimetroNoExiste() {
        ImportacionAsignacionesResponse resp = service.importarExcel(excel(new String[][]{
                {"999", "ACME", "Juan", "MarcaA", "Porta gringo", "2T2025", "http://a"},
        }));
        assertEquals(1, resp.getFallidas());
        assertEquals(0, resp.getCreados());
    }
}
