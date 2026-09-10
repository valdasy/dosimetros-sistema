package com.dosimetros.backend.chilexpress.controller;

import com.dosimetros.backend.chilexpress.dto.OtChilexpressResponse;
import com.dosimetros.backend.chilexpress.dto.PanelChilexpressResponse;
import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import com.dosimetros.backend.chilexpress.service.ChilexpressImportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Módulo Seguimiento Chilexpress. Autocontenido (sin relación con el resto del
 * sistema). Carga solo Administrador; búsqueda para Administrador y Ejecutivo.
 */
@RestController
@RequestMapping("/api/chilexpress")
public class ChilexpressController {

    /** Laboratorios (dato propio del módulo). */
    private static final List<String> EMPRESAS = List.of("Dosimet", "Photomat");

    /** Tope de resultados de búsqueda (evita traer toda la base). */
    private static final int MAX_RESULTADOS = 1000;

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

    /** Panel de alertas: OT en sucursal para retiro y OT con problema (siempre visible). */
    @GetMapping("/panel")
    @PreAuthorize("hasAnyRole('ADMIN', 'EJECUTIVO')")
    public ResponseEntity<PanelChilexpressResponse> panel() {
        List<ChilexpressOt> retiro = repo.retiroEnSucursal();
        List<ChilexpressOt> revision = repo.conProblema();

        // Pendientes de entrega: sin fecha de entrega y que NO estén ya en
        // sucursal ni con problema (listas mutuamente excluyentes).
        Set<Integer> yaListadas = new HashSet<>();
        retiro.forEach(o -> yaListadas.add(o.getId()));
        revision.forEach(o -> yaListadas.add(o.getId()));
        List<ChilexpressOt> pendientes = repo.findByFechaEntregaIsNullOrderByActualizadoEnDesc()
                .stream()
                .filter(o -> !yaListadas.contains(o.getId()))
                .filter(o -> !entregado(o.getEstado()))       // por si "EN DESCARGO" llega sin fecha
                .filter(o -> !creadaNoRecibida(o.getEstado())) // "EN PRE-RECEPCION": aún no se envía
                .toList();

        return ResponseEntity.ok(new PanelChilexpressResponse(
                retiro.stream().map(OtChilexpressResponse::new).toList(),
                revision.stream().map(OtChilexpressResponse::new).toList(),
                pendientes.stream().map(OtChilexpressResponse::new).toList()));
    }

    /** Nombres de destinatario para el autocompletado del buscador. */
    @GetMapping("/clientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EJECUTIVO')")
    public ResponseEntity<List<String>> clientes(
            @RequestParam(value = "empresa", required = false) String empresa) {
        return ResponseEntity.ok(repo.clientesDistinct(vacio(empresa) ? null : empresa));
    }

    /**
     * Búsqueda del listado. Requiere al menos un filtro (texto, rango de fecha o
     * estado); sin ninguno devuelve vacío para no traer toda la base. Resultados
     * acotados a {@value #MAX_RESULTADOS}.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'EJECUTIVO')")
    public ResponseEntity<List<OtChilexpressResponse>> buscar(
            @RequestParam(value = "empresa", required = false) String empresa,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "fechaTipo", required = false, defaultValue = "entrega") String fechaTipo,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        String texto = vacio(q) ? null : q.trim();
        String cat = vacio(estado) ? null : estado.trim().toLowerCase();

        // Al menos un filtro real (la empresa por sí sola no basta).
        if (texto == null && cat == null && desde == null && hasta == null) {
            return ResponseEntity.ok(List.of());
        }

        String emp = vacio(empresa) ? null : empresa;
        boolean porEntrega = !"periodo".equalsIgnoreCase(fechaTipo);
        List<ChilexpressOt> res = repo.buscar(emp, texto, porEntrega, desde, hasta, cat,
                PageRequest.of(0, MAX_RESULTADOS));
        return ResponseEntity.ok(res.stream().map(OtChilexpressResponse::new).toList());
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** ¿El estado indica entregado? ("EN DESCARGO" o variantes con "entreg"). */
    private static boolean entregado(String estado) {
        String e = estado == null ? "" : estado.toLowerCase();
        return e.contains("descargo") || e.contains("entreg");
    }

    /** ¿La OT solo fue creada y aún no la recibió Chilexpress? ("EN PRE-RECEPCION"). */
    private static boolean creadaNoRecibida(String estado) {
        String e = estado == null ? "" : estado.toLowerCase();
        return e.contains("pre") && e.contains("recepcion");
    }

    private String validarEmpresa(String empresa) {
        return EMPRESAS.stream()
                .filter(e -> e.equalsIgnoreCase(empresa == null ? "" : empresa.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Empresa no válida: " + empresa));
    }
}
