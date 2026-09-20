package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.cliente.ClienteRequest;
import com.dosimetros.backend.dto.cliente.ClienteResponse;
import com.dosimetros.backend.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    // ADMIN/OPERADOR ven todos los clientes. El EJECUTIVO usa /api/ejecutivo/mis-clientes
    // para ver únicamente los suyos (HU #3).
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<ClienteResponse>> listarActivos(
            @RequestParam(required = false) Integer ejecutivoId,
            @RequestParam(required = false) Integer empresaId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos) {
        // #16: si vienen filtros, se aplican; sin filtros devuelve todos los activos.
        boolean sinFiltros = ejecutivoId == null && empresaId == null && (q == null || q.isBlank());
        if (sinFiltros && !incluirInactivos) {
            return ResponseEntity.ok(clienteService.listarActivos());
        }
        return ResponseEntity.ok(clienteService.filtrar(ejecutivoId, empresaId, q, incluirInactivos));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ClienteResponse> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ClienteResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        clienteService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    // Reactiva un cliente dado de baja.
    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> reactivar(@PathVariable Integer id) {
        clienteService.reactivar(id);
        return ResponseEntity.noContent().build();
    }

    // Uso del cliente (nº de asignaciones), para decidir si se puede eliminar.
    @GetMapping("/{id}/uso")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<java.util.Map<String, Long>> uso(@PathVariable Integer id) {
        return ResponseEntity.ok(java.util.Map.of("asignaciones", clienteService.contarAsignaciones(id)));
    }

    // Borrado físico: solo para clientes creados por error (sin historial).
    // Si tiene asignaciones, el servicio lo bloquea (409).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
