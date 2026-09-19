package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.ejecutivo.EjecutivoRequest;
import com.dosimetros.backend.dto.ejecutivo.EjecutivoResponse;
import com.dosimetros.backend.dto.ejecutivo.UsoEjecutivoResponse;
import com.dosimetros.backend.service.EjecutivoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ejecutivos")
public class EjecutivoController {

    private final EjecutivoService ejecutivoService;

    public EjecutivoController(EjecutivoService ejecutivoService) {
        this.ejecutivoService = ejecutivoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<EjecutivoResponse>> listarActivos() {
        return ResponseEntity.ok(ejecutivoService.listarActivos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<EjecutivoResponse> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(ejecutivoService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<EjecutivoResponse> crear(@Valid @RequestBody EjecutivoRequest request) {
        return ResponseEntity.ok(ejecutivoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<EjecutivoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody EjecutivoRequest request) {
        return ResponseEntity.ok(ejecutivoService.actualizar(id, request));
    }

    // Impacto de desactivar (clientes activos + asignaciones), para avisar en la UI.
    @GetMapping("/{id}/uso")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<UsoEjecutivoResponse> uso(@PathVariable Integer id) {
        return ResponseEntity.ok(ejecutivoService.uso(id));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        ejecutivoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
