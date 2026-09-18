package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.asignacion.AsignacionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.AsignacionMasivaResponse;
import com.dosimetros.backend.dto.asignacion.AsignacionRequest;
import com.dosimetros.backend.dto.asignacion.AsignacionResponse;
import com.dosimetros.backend.dto.asignacion.ConteoClienteTrimestreResponse;
import com.dosimetros.backend.dto.asignacion.CorreccionExcelResponse;
import com.dosimetros.backend.dto.asignacion.CorreccionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.EditarAsignacionRequest;
import com.dosimetros.backend.dto.asignacion.ImportacionAsignacionesResponse;
import com.dosimetros.backend.dto.asignacion.LiberacionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.LiberacionPreviewResponse;
import com.dosimetros.backend.dto.asignacion.MarcarEnvioRequest;
import com.dosimetros.backend.service.AsignacionService;
import com.dosimetros.backend.service.ImportacionAsignacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    private final AsignacionService service;
    private final ImportacionAsignacionService importacionService;

    public AsignacionController(AsignacionService service,
                               ImportacionAsignacionService importacionService) {
        this.service = service;
        this.importacionService = importacionService;
    }

    @GetMapping("/dosimetro/{dosimetroId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<AsignacionResponse>> listarPorDosimetro(@PathVariable Integer dosimetroId) {
        return ResponseEntity.ok(service.listarPorDosimetro(dosimetroId));
    }

    @GetMapping("/cliente/{clienteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<AsignacionResponse>> listarPorCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(service.listarPorCliente(clienteId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<AsignacionResponse> crear(@Valid @RequestBody AsignacionRequest request) {
        AsignacionResponse response = service.crear(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/masivo")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<AsignacionMasivaResponse> asignarMasivo(
            @Valid @RequestBody AsignacionMasivaRequest request) {
        return ResponseEntity.status(201).body(service.asignarMasivo(request));
    }

    // #12: carga de asignaciones por archivo con upsert (clave = número).
    // validar=true hace una validación en seco (no persiste, solo previsualiza).
    @PostMapping("/importacion")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ImportacionAsignacionesResponse> importarAsignaciones(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "false") boolean validar) {
        return ResponseEntity.ok(importacionService.importarExcel(file, validar));
    }

    // Plantilla .xlsx para la carga de asignaciones por archivo.
    @GetMapping("/importacion/plantilla")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<byte[]> plantillaAsignaciones() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_asignaciones.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importacionService.plantillaExcel());
    }

    // Exporta a Excel un conjunto de asignaciones por sus ids (resumen de asignación).
    @PostMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<byte[]> exportarPorIds(@RequestBody List<Integer> ids) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asignaciones.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportarPorIds(ids));
    }

    // #14 (Correcciones): buscar asignaciones con filtros para corregir en lote.
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<AsignacionResponse>> buscarAsignaciones(
            @RequestParam(required = false) Integer clienteId,
            @RequestParam(required = false) Integer ejecutivoId,
            @RequestParam(required = false) Integer empresaId,
            @RequestParam(required = false) String trimestre,
            @RequestParam(required = false) Integer tipoPortaId,
            @RequestParam(required = false) String link) {
        return ResponseEntity.ok(
                service.buscarAsignaciones(clienteId, ejecutivoId, empresaId, trimestre, tipoPortaId, link));
    }

    // #14: editar una asignación existente del historial (incluido el cliente).
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<AsignacionResponse> editarAsignacion(
            @PathVariable Integer id,
            @Valid @RequestBody EditarAsignacionRequest request) {
        return ResponseEntity.ok(service.editarAsignacion(id, request));
    }

    // #14 (Correcciones): corregir un mismo campo en varias asignaciones a la vez.
    @PatchMapping("/correccion-masiva")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Integer> correccionMasiva(@Valid @RequestBody CorreccionMasivaRequest request) {
        return ResponseEntity.ok(service.correccionMasiva(request));
    }

    // #14 (Correcciones por Excel): descargar las asignaciones a un Excel editable.
    @PostMapping("/correcciones/exportar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<byte[]> exportarCorreccion(@RequestBody List<Integer> ids) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"correcciones_asignaciones.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportarParaCorreccion(ids));
    }

    // #14 (Correcciones por Excel): aplicar el Excel editado (clave = id_asignacion).
    @PostMapping("/correcciones/importar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<CorreccionExcelResponse> importarCorreccion(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importarCorreccion(file));
    }

    // Corrección — vista previa: cuántas asignaciones se liberarían (sin aplicar).
    @PostMapping("/liberacion/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LiberacionPreviewResponse> previewLiberacion(
            @Valid @RequestBody LiberacionMasivaRequest request) {
        return ResponseEntity.ok(service.previsualizarLiberacion(request));
    }

    // Corrección — aplicar: BORRA las asignaciones que coinciden y devuelve sus
    // dosímetros a "disponible". Solo Administrador (elimina historial). Devuelve
    // la cantidad liberada.
    @PostMapping("/liberacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> liberarMasivo(
            @Valid @RequestBody LiberacionMasivaRequest request) {
        return ResponseEntity.ok(service.liberarMasivo(request));
    }

    // #18: asignaciones por estado de envío (admin/operador, con filtro de ejecutivo).
    // enviado=false (por defecto) => pendientes; enviado=true => ya despachadas.
    @GetMapping("/pendientes-envio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<AsignacionResponse>> pendientesEnvio(
            @RequestParam(required = false) Integer ejecutivoId,
            @RequestParam(required = false) String trimestre,
            @RequestParam(required = false, defaultValue = "false") boolean enviado) {
        return ResponseEntity.ok(service.porEstadoEnvio(ejecutivoId, trimestre, enviado));
    }

    // #18: comparación por trimestres — conteo de asignaciones por cliente y trimestre.
    @GetMapping("/resumen-cliente-trimestre")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<ConteoClienteTrimestreResponse>> resumenClienteTrimestre(
            @RequestParam(required = false) Integer ejecutivoId) {
        return ResponseEntity.ok(service.conteoPorClienteTrimestre(ejecutivoId));
    }

    // #18: marcar asignaciones como enviadas (o revertir).
    @PatchMapping("/envio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Integer> marcarEnvio(@Valid @RequestBody MarcarEnvioRequest request) {
        return ResponseEntity.ok(service.marcarEnvio(request.getAsignacionIds(), request.isEnviado()));
    }
}
