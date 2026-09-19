package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.asignacion.AsignacionResponse;
import com.dosimetros.backend.dto.asignacion.LoteAsignacionResponse;
import com.dosimetros.backend.dto.asignacion.MisFiltrosResponse;
import com.dosimetros.backend.dto.cliente.ClienteResponse;
import com.dosimetros.backend.service.AsignacionService;
import com.dosimetros.backend.service.ClienteService;
import com.dosimetros.backend.service.CurrentUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas acotadas al ejecutivo autenticado (HU #3 y #15).
 * Cada endpoint resuelve el ejecutivo del usuario logueado, de modo que
 * solo ve sus propios clientes y asignaciones.
 */
@RestController
@RequestMapping("/api/ejecutivo")
@PreAuthorize("hasRole('EJECUTIVO')")
public class ConsultaEjecutivoController {

    private final CurrentUserService currentUserService;
    private final AsignacionService asignacionService;
    private final ClienteService clienteService;

    public ConsultaEjecutivoController(CurrentUserService currentUserService,
                                       AsignacionService asignacionService,
                                       ClienteService clienteService) {
        this.currentUserService = currentUserService;
        this.asignacionService = asignacionService;
        this.clienteService = clienteService;
    }

    @GetMapping("/mis-clientes")
    public ResponseEntity<List<ClienteResponse>> misClientes() {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(clienteService.listarPorEjecutivo(ejecutivoId));
    }

    @GetMapping("/mis-asignaciones")
    public ResponseEntity<List<AsignacionResponse>> misAsignaciones(
            @RequestParam(required = false) Integer clienteId,
            @RequestParam(required = false) String trimestre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer tipoPortaId,
            @RequestParam(required = false) Integer numeroBandeja,
            @RequestParam(required = false) Integer slotBandeja,
            @RequestParam(required = false) String link) {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.filtrarPorEjecutivo(
                ejecutivoId, clienteId, trimestre, fecha, tipoPortaId, numeroBandeja, slotBandeja, link));
    }

    // Trimestres (distintos) de un cliente del ejecutivo, para los chips de filtro
    // sin traer todas las asignaciones (carga perezosa por trimestre).
    @GetMapping("/clientes/{clienteId}/trimestres")
    public ResponseEntity<List<String>> misTrimestresDeCliente(@PathVariable Integer clienteId) {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.trimestresDeCliente(clienteId, ejecutivoId));
    }

    // #15: exportar a Excel las asignaciones del ejecutivo (con los mismos filtros).
    @GetMapping("/mis-asignaciones/export")
    public ResponseEntity<byte[]> exportarMisAsignaciones(
            @RequestParam(required = false) Integer clienteId,
            @RequestParam(required = false) String trimestre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer tipoPortaId,
            @RequestParam(required = false) Integer numeroBandeja,
            @RequestParam(required = false) Integer slotBandeja,
            @RequestParam(required = false) String link) {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        List<AsignacionResponse> asignaciones = asignacionService.filtrarPorEjecutivo(
                ejecutivoId, clienteId, trimestre, fecha, tipoPortaId, numeroBandeja, slotBandeja, link);
        byte[] data = asignacionService.exportarAsignacionesExcel(asignaciones);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mis-asignaciones.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // #18: asignaciones del ejecutivo por estado de envío (solo las suyas).
    @GetMapping("/pendientes-envio")
    public ResponseEntity<List<AsignacionResponse>> misPendientesEnvio(
            @RequestParam(required = false) String trimestre,
            @RequestParam(required = false, defaultValue = "false") boolean enviado) {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.porEstadoEnvio(ejecutivoId, trimestre, enviado));
    }

    // #18: comparación por trimestres del ejecutivo (solo sus clientes).
    @GetMapping("/resumen-cliente-trimestre")
    public ResponseEntity<List<com.dosimetros.backend.dto.asignacion.ConteoClienteTrimestreResponse>> misResumenClienteTrimestre() {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.conteoPorClienteTrimestre(ejecutivoId));
    }

    // Igual pero desglosado por tipo de porta (para totalizar los pendientes por porta).
    @GetMapping("/resumen-cliente-trimestre-porta")
    public ResponseEntity<List<com.dosimetros.backend.dto.asignacion.ConteoClienteTrimestrePortaResponse>> misResumenClienteTrimestrePorta() {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.conteoPorClienteTrimestrePorta(ejecutivoId));
    }

    @GetMapping("/mis-filtros")
    public ResponseEntity<MisFiltrosResponse> misFiltros() {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.opcionesFiltroEjecutivo(ejecutivoId));
    }

    @GetMapping("/mis-lotes")
    public ResponseEntity<List<LoteAsignacionResponse>> misLotes() {
        Integer ejecutivoId = currentUserService.requireEjecutivoId();
        return ResponseEntity.ok(asignacionService.listarLotesPorEjecutivo(ejecutivoId));
    }
}
