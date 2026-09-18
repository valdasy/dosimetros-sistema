package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.asignacion.AsignacionResponse;
import com.dosimetros.backend.dto.dosimetro.ActualizarTipoPortaRangoRequest;
import com.dosimetros.backend.dto.dosimetro.ActualizarTipoPortaRangoResponse;
import com.dosimetros.backend.dto.dosimetro.ArmarSeleccionRequest;
import com.dosimetros.backend.dto.dosimetro.DosimetroDetalleResponse;
import com.dosimetros.backend.dto.dosimetro.DosimetroRequest;
import com.dosimetros.backend.dto.dosimetro.DosimetroResponse;
import com.dosimetros.backend.dto.dosimetro.DuplicadoResponse;
import com.dosimetros.backend.dto.dosimetro.EditarEspecificacionesRequest;
import com.dosimetros.backend.dto.dosimetro.MatrizCeldaResponse;
import com.dosimetros.backend.dto.dosimetro.PortaDisponibleResponse;
import com.dosimetros.backend.dto.dosimetro.TareaArmadoResponse;
import com.dosimetros.backend.dto.tarea.EliminarTareasResponse;
import com.dosimetros.backend.dto.tarea.TareaDisponibleResponse;
import com.dosimetros.backend.dto.tarea.TareaEliminableResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.dosimetros.backend.service.AsignacionService;
import com.dosimetros.backend.service.DosimetroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/dosimetros")
public class DosimetroController {

    private final DosimetroService service;
    private final AsignacionService asignacionService;

    public DosimetroController(DosimetroService service, AsignacionService asignacionService) {
        this.service = service;
        this.asignacionService = asignacionService;
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroResponse>> listarDisponibles() {
        return ResponseEntity.ok(service.listarDisponibles());
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroResponse>> filtrarStock(
            @RequestParam(required = false) Integer tipoDosimetroId,
            @RequestParam(required = false) Integer tipoPortaId,
            @RequestParam(required = false) Integer tareaId,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.filtrarStock(tipoDosimetroId, tipoPortaId, tareaId, estado));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroDetalleResponse>> buscarPorNumero(@RequestParam Integer numero) {
        return ResponseEntity.ok(service.buscarDetallePorNumero(numero));
    }

    // #13 (individual): dosímetros disponibles con un número (para asignar uno concreto).
    @GetMapping("/disponible-por-numero")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroResponse>> disponiblesPorNumero(@RequestParam Integer numero) {
        return ResponseEntity.ok(service.disponiblesPorNumero(numero));
    }

    // #14 (Edición): todos los dosímetros con un número (con o sin asignación).
    @GetMapping("/detalle")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroDetalleResponse>> detallePorNumero(@RequestParam Integer numero) {
        return ResponseEntity.ok(service.detalleTodosPorNumero(numero));
    }

    // #14 (Edición): editar especificaciones (sin tarea/bandeja/slot).
    @PatchMapping("/{id}/especificaciones")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<DosimetroResponse> editarEspecificaciones(
            @PathVariable Integer id,
            @Valid @RequestBody EditarEspecificacionesRequest request) {
        return ResponseEntity.ok(service.editarEspecificaciones(id, request));
    }

    @GetMapping("/duplicados")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DuplicadoResponse>> duplicados() {
        return ResponseEntity.ok(service.listarDuplicados());
    }

    @GetMapping("/disponibles/portas")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<PortaDisponibleResponse>> portasDisponibles() {
        return ResponseEntity.ok(service.detallePortasDisponibles());
    }

    // #5: todas las portas, incluidas las que están en 0.
    @GetMapping("/stock/portas")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<PortaDisponibleResponse>> stockTodasLasPortas() {
        return ResponseEntity.ok(service.stockTodasLasPortas());
    }

    // #6: matriz tarea × porta para la vista dinámica.
    @GetMapping("/stock/matriz")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<MatrizCeldaResponse>> stockMatriz(
            @RequestParam(required = false) Integer tipoDosimetroId,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.matrizTareaPorta(tipoDosimetroId, estado));
    }

    @GetMapping("/tareas-disponibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<TareaDisponibleResponse>> tareasDisponibles(
            @RequestParam(required = false) Integer tipoDosimetroId) {
        return ResponseEntity.ok(service.tareasConDisponibles(tipoDosimetroId));
    }

    @GetMapping("/stock/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<byte[]> exportarStock(
            @RequestParam(required = false) Integer tipoDosimetroId,
            @RequestParam(required = false) Integer tipoPortaId,
            @RequestParam(required = false) String estado) {
        byte[] data = service.exportarStockExcel(tipoDosimetroId, tipoPortaId, estado);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<DosimetroResponse> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<AsignacionResponse>> historial(@PathVariable Integer id) {
        return ResponseEntity.ok(asignacionService.listarPorDosimetro(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DosimetroResponse> crear(@Valid @RequestBody DosimetroRequest request) {
        DosimetroResponse response = service.crear(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DosimetroResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody DosimetroRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/rango-porta")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ActualizarTipoPortaRangoResponse> actualizarTipoPortaPorRango(
            @Valid @RequestBody ActualizarTipoPortaRangoRequest request) {
        return ResponseEntity.ok(service.actualizarTipoPortaPorRango(request));
    }

    // #7: resumen de armado de todas las tareas.
    @GetMapping("/tareas/resumen-armado")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<TareaArmadoResponse>> resumenArmado() {
        return ResponseEntity.ok(service.resumenArmadoPorTarea());
    }

    // #7: dosímetros de una tarea (mapa de bandejas/slots con estado de armado).
    @GetMapping("/tareas/{tareaId}/dosimetros")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<DosimetroResponse>> dosimetrosDeTarea(@PathVariable Integer tareaId) {
        return ResponseEntity.ok(service.dosimetrosDeTarea(tareaId));
    }

    // #7 (modo preciso): arma una selección concreta de dosímetros.
    @PatchMapping("/porta-seleccion")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ActualizarTipoPortaRangoResponse> armarSeleccion(
            @Valid @RequestBody ArmarSeleccionRequest request) {
        return ResponseEntity.ok(service.armarSeleccion(request.getDosimetroIds(), request.getTipoPortaId()));
    }

    @PatchMapping("/{id}/liberar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> liberar(@PathVariable Integer id) {
        service.liberar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/danado")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<DosimetroResponse> marcarDanado(@PathVariable Integer id) {
        return ResponseEntity.ok(service.marcarDanado(id));
    }

    @PatchMapping("/{id}/bueno")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<DosimetroResponse> marcarBueno(@PathVariable Integer id) {
        return ResponseEntity.ok(service.marcarBueno(id));
    }

    @PatchMapping("/{id}/stock-emergencia")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DosimetroResponse> marcarStockEmergencia(
            @PathVariable Integer id,
            @RequestParam(required = false) String observacion) {
        return ResponseEntity.ok(service.marcarStockEmergencia(id, observacion));
    }

    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> darDeBaja(
            @PathVariable Integer id,
            @RequestParam(required = false) String observacion) {
        service.darDeBaja(id, observacion);
        return ResponseEntity.noContent().build();
    }

    // Corrección de carga — lista de tareas con conteos y si son eliminables.
    @GetMapping("/tareas/eliminables")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TareaEliminableResponse>> tareasEliminables() {
        return ResponseEntity.ok(service.listarTareasEliminables());
    }

    // Corrección de carga — elimina tareas y sus dosímetros (solo si están
    // 100% disponibles y sin historial). Solo Administrador; borrado permanente.
    @PostMapping("/tareas/eliminar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EliminarTareasResponse> eliminarTareas(@RequestBody List<Integer> tareaIds) {
        return ResponseEntity.ok(service.eliminarTareas(tareaIds));
    }
}
