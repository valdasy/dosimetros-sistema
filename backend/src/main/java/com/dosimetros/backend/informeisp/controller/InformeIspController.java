package com.dosimetros.backend.informeisp.controller;

import com.dosimetros.backend.informeisp.dto.AnalisisResponse;
import com.dosimetros.backend.informeisp.dto.EstadoMaestraResponse;
import com.dosimetros.backend.informeisp.model.FilaInforme;
import com.dosimetros.backend.informeisp.model.ResultadoProceso;
import com.dosimetros.backend.informeisp.repository.IspClienteTecnologiaRepository;
import com.dosimetros.backend.informeisp.repository.IspPersonaCodigoRepository;
import com.dosimetros.backend.informeisp.service.AsignacionCodigosService;
import com.dosimetros.backend.informeisp.service.InformeReaderService;
import com.dosimetros.backend.informeisp.service.IspExcelWriterService;
import com.dosimetros.backend.informeisp.service.IspMappings;
import com.dosimetros.backend.informeisp.service.MaestraImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Módulo Informe ISP (RND). Importa las maestras aprendidas y genera el Excel
 * dosimétrico trimestral para el ISP. Solo Administrador. Ver docs/INFORME_ISP.md.
 *
 * Módulo autocontenido: la empresa (laboratorio) es un dato propio del módulo
 * —los dos laboratorios que emiten dosimetría— y no tiene relación con las
 * tablas del resto del sistema.
 */
@RestController
@RequestMapping("/api/isp")
@PreAuthorize("hasRole('ADMIN')")
public class InformeIspController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Laboratorios que maneja el módulo ISP (dato propio, sin FK al sistema). */
    private static final List<String> EMPRESAS = List.of("Dosimet", "Photomat");

    private final IspPersonaCodigoRepository personaRepo;
    private final IspClienteTecnologiaRepository clienteTecRepo;
    private final InformeReaderService informeReader;
    private final MaestraImportService maestraImport;
    private final AsignacionCodigosService asignacion;
    private final IspExcelWriterService writer;

    public InformeIspController(IspPersonaCodigoRepository personaRepo,
                                IspClienteTecnologiaRepository clienteTecRepo,
                                InformeReaderService informeReader,
                                MaestraImportService maestraImport,
                                AsignacionCodigosService asignacion,
                                IspExcelWriterService writer) {
        this.personaRepo = personaRepo;
        this.clienteTecRepo = clienteTecRepo;
        this.informeReader = informeReader;
        this.maestraImport = maestraImport;
        this.asignacion = asignacion;
        this.writer = writer;
    }

    /** Laboratorios disponibles para el módulo (lista propia). */
    @GetMapping("/empresas")
    public ResponseEntity<List<String>> empresas() {
        return ResponseEntity.ok(EMPRESAS);
    }

    /** Estado de las maestras cargadas. */
    @GetMapping("/estado")
    public ResponseEntity<EstadoMaestraResponse> estado() {
        return ResponseEntity.ok(new EstadoMaestraResponse(personaRepo.count(), clienteTecRepo.count()));
    }

    /** Importa las maestras desde un informe ISP ya entregado (hoja DOSIS). */
    @PostMapping("/maestra/importar")
    public ResponseEntity<Map<String, Integer>> importarMaestra(
            @RequestParam("empresa") String empresa,
            @RequestParam("file") MultipartFile file) throws IOException {

        validarArchivo(file);
        String emp = validarEmpresa(empresa);
        return ResponseEntity.ok(maestraImport.importar(file, emp));
    }

    /** Vista previa: procesa el informe crudo y devuelve resumen + inconsistencias. */
    @PostMapping("/informe/analizar")
    public ResponseEntity<AnalisisResponse> analizar(
            @RequestParam("empresa") String empresa,
            @RequestParam("file") MultipartFile file) throws IOException {

        validarArchivo(file);
        String emp = validarEmpresa(empresa);
        List<FilaInforme> filas = informeReader.leer(file);
        ResultadoProceso res = asignacion.procesar(emp, filas);
        return ResponseEntity.ok(new AnalisisResponse(emp, res.resumen(), res.inconsistencias));
    }

    /** Genera el Excel del ISP (TOES + DOSIS + REVISION). */
    @PostMapping("/informe/generar")
    public ResponseEntity<byte[]> generar(
            @RequestParam("empresa") String empresa,
            @RequestParam("file") MultipartFile file) throws IOException {

        validarArchivo(file);
        String emp = validarEmpresa(empresa);
        List<FilaInforme> filas = informeReader.leer(file);
        ResultadoProceso res = asignacion.procesar(emp, filas);
        byte[] excel = writer.escribir(res);

        String filename = "informe_isp_" + emp.toLowerCase() + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX))
                .body(excel);
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Falta el archivo.");
        }
    }

    /** Valida contra la lista propia y devuelve el nombre canónico del laboratorio. */
    private String validarEmpresa(String empresa) {
        String norm = IspMappings.norm(empresa);
        return EMPRESAS.stream()
                .filter(e -> IspMappings.norm(e).equals(norm))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Empresa no válida: " + empresa));
    }
}
