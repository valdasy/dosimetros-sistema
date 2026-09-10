package com.dosimetros.backend.chilexpress.controller;

import com.dosimetros.backend.chilexpress.dto.OtChilexpressResponse;
import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import com.dosimetros.backend.chilexpress.service.ChilexpressImportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Módulo Seguimiento Chilexpress. Autocontenido (sin relación con el resto del
 * sistema). Carga solo Administrador; búsqueda para Administrador y Ejecutivo.
 */
@RestController
@RequestMapping("/api/chilexpress")
public class ChilexpressController {

    /** Laboratorios (dato propio del módulo). */
    private static final List<String> EMPRESAS = List.of("Dosimet", "Photomat");

    private final ChilexpressImportService importService;
    private final ChilexpressOtRepository repo;

    public ChilexpressController(ChilexpressImportService importService, ChilexpressOtRepository repo) {
        this.importService = importService;
        this.repo = repo;
    }

    @GetMapping("/empresas")
    @PreAuthorize("hasAnyRole('ADMIN', 'EJECUTIVO')")
    public ResponseEntity<List<String>> empresas() {
        return ResponseEntity.ok(EMPRESAS);
    }

    /** Carga/actualiza las OT desde el export de Chilexpress. Solo Administrador. */
    @PostMapping("/importar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> importar(
            @RequestParam("empresa") String empresa,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Falta el archivo.");
        }
        return ResponseEntity.ok(importService.importar(validarEmpresa(empresa), file));
    }

    /** Búsqueda por texto (destinatario/referencia/OT) y rango de fecha. */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'EJECUTIVO')")
    public ResponseEntity<List<OtChilexpressResponse>> buscar(
            @RequestParam(value = "empresa", required = false) String empresa,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "fechaTipo", required = false, defaultValue = "entrega") String fechaTipo,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        String emp = vacio(empresa) ? null : empresa;
        String texto = vacio(q) ? null : q.trim();
        List<ChilexpressOt> res = "periodo".equalsIgnoreCase(fechaTipo)
                ? repo.buscarPorPeriodo(emp, texto, desde, hasta)
                : repo.buscarPorEntrega(emp, texto, desde, hasta);
        return ResponseEntity.ok(res.stream().map(OtChilexpressResponse::new).toList());
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String validarEmpresa(String empresa) {
        return EMPRESAS.stream()
                .filter(e -> e.equalsIgnoreCase(empresa == null ? "" : empresa.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Empresa no válida: " + empresa));
    }
}
