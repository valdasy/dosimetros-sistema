package com.dosimetros.backend.controller;

import com.dosimetros.backend.dto.tipoporta.TipoPortaRequest;
import com.dosimetros.backend.dto.tipoporta.TipoPortaResponse;
import com.dosimetros.backend.dto.tipoporta.UsoTipoPortaResponse;
import com.dosimetros.backend.service.TipoPortaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-porta")
public class TipoPortaController {

    private final TipoPortaService tipoPortaService;

    public TipoPortaController(TipoPortaService tipoPortaService) {
        this.tipoPortaService = tipoPortaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<TipoPortaResponse>> listar() {
        return ResponseEntity.ok(tipoPortaService.listar());
    }

    @GetMapping("/por-tipo-dosimetro/{tipoDosimetroId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<List<TipoPortaResponse>> listarPorTipoDosimetro(@PathVariable Integer tipoDosimetroId) {
        return ResponseEntity.ok(tipoPortaService.listarPorTipoDosimetro(tipoDosimetroId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<TipoPortaResponse> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(tipoPortaService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<TipoPortaResponse> crear(@Valid @RequestBody TipoPortaRequest request) {
        return ResponseEntity.ok(tipoPortaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<TipoPortaResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody TipoPortaRequest request) {
        return ResponseEntity.ok(tipoPortaService.actualizar(id, request));
    }

    // Uso del tipo de porta (dosímetros/asignaciones que lo referencian), para
    // avisar antes de eliminar y saber si hay una porta "Sin armar" de respaldo.
    @GetMapping("/{id}/uso")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<UsoTipoPortaResponse> uso(@PathVariable Integer id) {
        return ResponseEntity.ok(tipoPortaService.uso(id));
    }

    // Al eliminar: si está en uso, confirmar=true reasigna el histórico a la porta
    // "Sin armar" de la misma tecnología antes de borrar (no se pierde el histórico).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> eliminar(
            @PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "false") boolean confirmar) {
        tipoPortaService.eliminar(id, confirmar);
        return ResponseEntity.noContent().build();
    }
}
