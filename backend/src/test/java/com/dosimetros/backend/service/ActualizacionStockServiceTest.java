package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.dosimetro.ActualizacionStockResponse;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Tarea;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.TareaRepository;
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

/**
 * Verifica el upsert de stock por archivo (#8): crear, actualizar, sin cambios
 * y las guardas (número duplicado en el sistema, dosímetro asignado).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ActualizacionStockServiceTest {

    @Autowired private DosimetroRepository dosimetroRepository;
    @Autowired private TipoDosimetroRepository tipoDosimetroRepository;
    @Autowired private TipoPortaRepository tipoPortaRepository;
    @Autowired private TareaRepository tareaRepository;

    private ImportacionDosimetroServiceImpl service;
    private TipoDosimetro tld;
    private TipoPorta gringo;
    private TipoPorta viejo;
    private Tarea tarea1765;

    @BeforeEach
    void setUp() {
        service = new ImportacionDosimetroServiceImpl(
                dosimetroRepository, tipoDosimetroRepository, tipoPortaRepository, tareaRepository);

        tld = new TipoDosimetro();
        tld.setNombre("TLD");
        tld = tipoDosimetroRepository.save(tld);

        gringo = nuevaPorta("Gringo");
        viejo = nuevaPorta("Viejo");

        tarea1765 = new Tarea();
        tarea1765.setNumeroTarea("1765");
        tarea1765.setFechaCreacion(LocalDate.of(2025, 1, 1));
        tarea1765 = tareaRepository.save(tarea1765);
    }

    private TipoPorta nuevaPorta(String nombre) {
        TipoPorta tp = new TipoPorta();
        tp.setNombre(nombre);
        tp.setTipoDosimetro(tld);
        return tipoPortaRepository.save(tp);
    }

    private Dosimetro seedDosimetro(int numero, TipoPorta porta, int banda, int slot, String estado) {
        Dosimetro d = new Dosimetro();
        d.setNumero(numero);
        d.setTipoDosimetro(tld);
        d.setTipoPorta(porta);
        d.setTarea(tarea1765);
        d.setNumeroBandeja(banda);
        d.setSlotBandeja(slot);
        d.setEstado(estado);
        d.setFechaCreacion(LocalDate.of(2025, 1, 1));
        return dosimetroRepository.save(d);
    }

    // Construye un .xlsx en memoria. Cada fila: {numero, tipo, porta, tarea, banda, slot}.
    private MockMultipartFile excel(String[][] filas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Datos");
            Row header = sheet.createRow(0);
            String[] cols = {"numero", "tipo", "porta", "tarea", "banda", "slot"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < filas[r].length; c++) {
                    if (filas[r][c] != null) row.createCell(c).setCellValue(filas[r][c]);
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "stock.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void upsertCreaActualizaYDetectaSinCambios() {
        // Existente idéntico a la fila.
        seedDosimetro(100, gringo, 1, 1, "disponible");
        // Existente que la fila va a cambiar (Gringo -> Viejo).
        seedDosimetro(300, gringo, 2, 2, "disponible");

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"100", "TLD", "Gringo", "1765", "1", "1"}, // idéntico -> sin cambios
                {"200", "TLD", "Viejo", "1765", "5", "5"},  // nuevo    -> creado
                {"300", "TLD", "Viejo", "1765", "2", "2"},  // cambia   -> actualizado
        }));

        assertEquals(3, resp.getTotalFilas());
        assertEquals(1, resp.getCreados());
        assertEquals(1, resp.getActualizados());
        assertEquals(1, resp.getSinCambios());
        assertEquals(0, resp.getFallidas());

        // La porta del 300 quedó actualizada a Viejo.
        Dosimetro d300 = dosimetroRepository.findByNumeroOrderByIdAsc(300).get(0);
        assertEquals(viejo.getId(), d300.getTipoPorta().getId());
    }

    @Test
    void upsertRearmaDosimetroAsignadoYLoDejaDisponible() {
        // El dosímetro volvió a la oficina: aunque figure "asignado" a un cliente
        // anterior, se puede rearmar con una tarea nueva y queda disponible.
        seedDosimetro(500, gringo, 1, 1, "asignado");

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"500", "TLD", "Viejo", "1765", "9", "9"}, // se rearma
        }));

        assertEquals(0, resp.getFallidas());
        assertEquals(1, resp.getActualizados());
        Dosimetro d500 = dosimetroRepository.findByNumeroOrderByIdAsc(500).get(0);
        assertEquals(viejo.getId(), d500.getTipoPorta().getId());
        assertEquals("disponible", d500.getEstado());
    }

    @Test
    void upsertRecuperaDosimetroExtraviado() {
        // Un dosímetro extraviado que reaparece en la carga se rearma y vuelve a disponible.
        seedDosimetro(550, gringo, 1, 1, "extraviado");

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"550", "TLD", "Viejo", "1765", "4", "4"},
        }));

        assertEquals(0, resp.getFallidas());
        assertEquals(1, resp.getActualizados());
        Dosimetro d = dosimetroRepository.findByNumeroOrderByIdAsc(550).get(0);
        assertEquals("disponible", d.getEstado());
        assertEquals(viejo.getId(), d.getTipoPorta().getId());
    }

    @Test
    void upsertRecuperaExtraviadoAunqueElArmadoSeaIgual() {
        // Aunque el armado del archivo coincida, un extraviado igual se recupera
        // (no se trata como "sin cambios").
        seedDosimetro(560, gringo, 1, 1, "extraviado");

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"560", "TLD", "Gringo", "1765", "1", "1"},
        }));

        assertEquals(0, resp.getFallidas());
        assertEquals(0, resp.getSinCambios());
        Dosimetro d = dosimetroRepository.findByNumeroOrderByIdAsc(560).get(0);
        assertEquals("disponible", d.getEstado());
    }

    @Test
    void upsertNoTocaDosimetrosDadosDeBaja() {
        seedDosimetro(600, gringo, 1, 1, "baja");

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"600", "TLD", "Viejo", "1765", "9", "9"}, // dado de baja -> bloqueado
        }));

        assertEquals(false, resp.isAplicado());
        assertEquals(1, resp.getFallidas());
        assertEquals(0, resp.getActualizados());
        // Se mantiene la porta y el estado originales.
        Dosimetro d600 = dosimetroRepository.findByNumeroOrderByIdAsc(600).get(0);
        assertEquals(gringo.getId(), d600.getTipoPorta().getId());
        assertEquals("baja", d600.getEstado());
    }

    @Test
    void archivoConErroresNoAplicaNada() {
        // Una fila válida (nueva) y una inválida (tipo de dosímetro inexistente).
        // Al haber error, no se guarda nada y el resumen queda en cero.
        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"900", "TLD", "Gringo", "1765", "5", "5"}, // válida (sería creada)
                {"901", "NO_EXISTE", "Gringo", "1765", "6", "6"}, // tipo inexistente -> error
        }));

        assertEquals(false, resp.isAplicado());
        assertEquals(0, resp.getCreados());
        assertEquals(0, resp.getActualizados());
        assertEquals(0, resp.getSinCambios());
        assertEquals(1, resp.getFallidas());
        org.junit.jupiter.api.Assertions.assertFalse(resp.getErrores().isEmpty());
    }

    @Test
    void upsertCargaCopiaYAvisaSiElNumeroYaEstaArmadoEnOtraTarea() {
        // Existe el 800 disponible, armado en la tarea 1765.
        seedDosimetro(800, gringo, 1, 2, "disponible");
        // Tarea alternativa para simular un posible duplicado físico.
        Tarea tarea8440 = new Tarea();
        tarea8440.setNumeroTarea("8440");
        tarea8440.setFechaCreacion(LocalDate.of(2025, 1, 1));
        tareaRepository.save(tarea8440);

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"800", "TLD", "Gringo", "8440", "2", "1"}, // mismo número, otra tarea
        }));

        // No se sobrescribe: se carga una copia y se avisa (no bloquea).
        assertEquals(true, resp.isAplicado());
        assertEquals(0, resp.getActualizados());
        assertEquals(1, resp.getDuplicados());
        assertEquals(0, resp.getFallidas());
        org.junit.jupiter.api.Assertions.assertFalse(resp.getAlertas().isEmpty());
        // Ahora existen dos dosímetros con el número 800.
        assertEquals(2, dosimetroRepository.findByNumeroOrderByIdAsc(800).size());
    }

    @Test
    void upsertNoActualizaSiElNumeroEstaDuplicadoEnElSistema() {
        seedDosimetro(700, gringo, 1, 1, "disponible");
        seedDosimetro(700, viejo, 2, 2, "disponible"); // mismo número, duplicado real

        ActualizacionStockResponse resp = service.actualizarStockExcel(excel(new String[][]{
                {"700", "TLD", "Gringo", "1765", "3", "3"},
        }));

        assertEquals(1, resp.getFallidas());
        assertEquals(0, resp.getActualizados());
        assertEquals(0, resp.getCreados());
    }
}
