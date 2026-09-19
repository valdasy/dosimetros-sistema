package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.tipoporta.TipoPortaRequest;
import com.dosimetros.backend.dto.tipoporta.TipoPortaResponse;
import com.dosimetros.backend.dto.tipoporta.UsoTipoPortaResponse;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.exception.ResourceNotFoundException;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.TipoDosimetroRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TipoPortaService {

    private final TipoPortaRepository repository;
    private final TipoDosimetroRepository tipoDosimetroRepository;
    private final DosimetroRepository dosimetroRepository;
    private final AsignacionRepository asignacionRepository;

    public TipoPortaService(TipoPortaRepository repository, TipoDosimetroRepository tipoDosimetroRepository,
                            DosimetroRepository dosimetroRepository, AsignacionRepository asignacionRepository) {
        this.repository = repository;
        this.tipoDosimetroRepository = tipoDosimetroRepository;
        this.dosimetroRepository = dosimetroRepository;
        this.asignacionRepository = asignacionRepository;
    }

    private static boolean esSinArmar(TipoPorta tp) {
        return (tp.getNombre() == null ? "" : tp.getNombre()).toLowerCase().startsWith("sin armar");
    }

    // Porta "Sin armar" de la misma tecnología, distinta de la propia (destino del
    // histórico al eliminar). Devuelve null si no hay ninguna.
    private TipoPorta fallbackSinArmar(TipoPorta tp) {
        return repository.findSinArmarByTipoDosimetro(tp.getTipoDosimetro().getId())
                .stream()
                .filter(f -> !f.getId().equals(tp.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<TipoPortaResponse> listar() {
        return repository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TipoPortaResponse> listarPorTipoDosimetro(Integer tipoDosimetroId) {
        return repository.findByTipoDosimetroIdOrderByNombreAsc(tipoDosimetroId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TipoPortaResponse obtenerPorId(Integer id) {
        TipoPorta tp = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + id));
        return toResponse(tp);
    }

    public TipoPortaResponse crear(TipoPortaRequest request) {
        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findById(request.getTipoDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de dosímetro no encontrado con id: " + request.getTipoDosimetroId()));

        TipoPorta tp = new TipoPorta();
        tp.setNombre(request.getNombre());
        tp.setTipoDosimetro(tipoDosimetro);

        return toResponse(repository.save(tp));
    }

    public TipoPortaResponse actualizar(Integer id, TipoPortaRequest request) {
        TipoPorta tp = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + id));

        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findById(request.getTipoDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de dosímetro no encontrado con id: " + request.getTipoDosimetroId()));

        tp.setNombre(request.getNombre());
        tp.setTipoDosimetro(tipoDosimetro);

        return toResponse(repository.save(tp));
    }

    // Uso del tipo de porta (para avisar antes de eliminar).
    public UsoTipoPortaResponse uso(Integer id) {
        TipoPorta tp = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + id));
        TipoPorta fallback = fallbackSinArmar(tp);
        return new UsoTipoPortaResponse(
                dosimetroRepository.countByTipoPortaId(id),
                asignacionRepository.countByTipoPortaId(id),
                esSinArmar(tp),
                fallback != null,
                fallback != null ? fallback.getNombre() : null);
    }

    // Elimina un tipo de porta protegiendo el histórico:
    //  - No se puede eliminar una porta "Sin armar" (es el estado por defecto).
    //  - Si está en uso y no se confirma, se bloquea (409) para que el frontend avise.
    //  - Si se confirma, el histórico (dosímetros y asignaciones) se reasigna a la
    //    porta "Sin armar" de la misma tecnología y luego se elimina la porta.
    @Transactional
    public void eliminar(Integer id, boolean confirmar) {
        TipoPorta tp = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + id));

        // Porta "Sin armar" de respaldo de la misma tecnología (distinta de la propia).
        TipoPorta fallback = fallbackSinArmar(tp);

        // Solo se bloquea eliminar una "Sin armar" cuando es la ÚNICA de su
        // tecnología (es el estado por defecto donde se conserva el histórico).
        // Si hay otra "Sin armar" de la misma tecnología (duplicada), sí se puede
        // eliminar: el histórico se absorbe en la que queda.
        if (esSinArmar(tp) && fallback == null) {
            throw new IllegalArgumentException(
                    "No se puede eliminar '" + tp.getNombre() + "': es el único estado " +
                    "'Sin armar (" + tp.getTipoDosimetro().getNombre() + ")' de la tecnología, " +
                    "donde se conserva el histórico.");
        }

        long dosimetros = dosimetroRepository.countByTipoPortaId(id);
        long asignaciones = asignacionRepository.countByTipoPortaId(id);
        long total = dosimetros + asignaciones;

        if (total > 0) {
            if (!confirmar) {
                throw new IllegalStateException(
                        "El tipo de porta '" + tp.getNombre() + "' está en uso por " + dosimetros +
                        " dosímetro(s) y " + asignaciones + " asignación(es). Confirma para conservar " +
                        "el histórico como 'Sin armar'.");
            }
            if (fallback == null) {
                throw new IllegalArgumentException(
                        "No existe una porta 'Sin armar (" + tp.getTipoDosimetro().getNombre() + ")' para " +
                        "conservar el histórico. Créala antes de eliminar '" + tp.getNombre() + "'.");
            }
            if (dosimetros > 0) dosimetroRepository.reasignarTipoPorta(tp, fallback);
            if (asignaciones > 0) asignacionRepository.reasignarTipoPorta(tp, fallback);
        }

        repository.delete(tp);
    }

    private TipoPortaResponse toResponse(TipoPorta tp) {
        return new TipoPortaResponse(
                tp.getId(),
                tp.getNombre(),
                tp.getTipoDosimetro().getId(),
                tp.getTipoDosimetro().getNombre()
        );
    }
}