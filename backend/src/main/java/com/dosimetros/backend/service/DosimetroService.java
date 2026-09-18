package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.asignacion.AsignacionResponse;
import com.dosimetros.backend.dto.dosimetro.ActualizarTipoPortaRangoRequest;
import com.dosimetros.backend.dto.dosimetro.ActualizarTipoPortaRangoResponse;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.dosimetros.backend.entity.Asignacion;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Tarea;
import com.dosimetros.backend.entity.TipoDosimetro;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.exception.ResourceNotFoundException;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.TareaRepository;
import com.dosimetros.backend.repository.TipoDosimetroRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DosimetroService {

    private final DosimetroRepository dosimetroRepository;
    private final TipoDosimetroRepository tipoDosimetroRepository;
    private final TipoPortaRepository tipoPortaRepository;
    private final TareaRepository tareaRepository;
    private final AsignacionRepository asignacionRepository;

    public DosimetroService(DosimetroRepository dosimetroRepository,
                            TipoDosimetroRepository tipoDosimetroRepository,
                            TipoPortaRepository tipoPortaRepository,
                            TareaRepository tareaRepository,
                            AsignacionRepository asignacionRepository) {
        this.dosimetroRepository = dosimetroRepository;
        this.tipoDosimetroRepository = tipoDosimetroRepository;
        this.tipoPortaRepository = tipoPortaRepository;
        this.tareaRepository = tareaRepository;
        this.asignacionRepository = asignacionRepository;
    }

    public List<DosimetroResponse> listarDisponibles() {
        return dosimetroRepository.findByEstadoOrderByNumeroAsc("disponible")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // HU #7: stock filtrado por tipo de dosímetro, estado de armado (tipo de
    // porta, incluida "sin armar"), tarea y estado del dosímetro.
    public List<DosimetroResponse> filtrarStock(Integer tipoDosimetroId, Integer tipoPortaId,
                                                Integer tareaId, String estado) {
        // Estado en blanco = todos los estados (coincide con la matriz de #6).
        String estadoFinal = (estado == null || estado.isBlank()) ? null : estado;
        return dosimetroRepository.filtrar(tipoDosimetroId, tipoPortaId, tareaId, estadoFinal)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Tareas que tienen al menos un dosímetro disponible (opcional: de un tipo).
    public List<TareaDisponibleResponse> tareasConDisponibles(Integer tipoDosimetroId) {
        return dosimetroRepository.tareasConDisponibles(tipoDosimetroId).stream()
                .map(o -> new TareaDisponibleResponse((Integer) o[0], (String) o[1], (Long) o[2]))
                .toList();
    }

    // Detalle de stock disponible agrupado por porta (con su tipo de dosímetro).
    public List<PortaDisponibleResponse> detallePortasDisponibles() {
        return dosimetroRepository.detallePortasDisponibles().stream()
                .map(o -> new PortaDisponibleResponse(
                        (Integer) o[0], (String) o[1], (String) o[2], (Long) o[3]))
                .toList();
    }

    // #5: TODAS las portas con su stock disponible, incluidas las que están en 0.
    public List<PortaDisponibleResponse> stockTodasLasPortas() {
        return dosimetroRepository.stockTodasLasPortas().stream()
                .map(o -> new PortaDisponibleResponse(
                        (Integer) o[0], (String) o[1], (String) o[2], (Long) o[3]))
                .toList();
    }

    // #6: matriz tarea × porta para la vista dinámica de stock.
    public List<MatrizCeldaResponse> matrizTareaPorta(Integer tipoDosimetroId, String estado) {
        String estadoFinal = (estado == null || estado.isBlank()) ? null : estado;
        return dosimetroRepository.matrizTareaPorta(estadoFinal, tipoDosimetroId).stream()
                .map(o -> new MatrizCeldaResponse(
                        (Integer) o[0], (String) o[1], (Integer) o[2], (String) o[3], (Long) o[4]))
                .toList();
    }

    // Exporta el stock filtrado a un archivo Excel (.xlsx).
    public byte[] exportarStockExcel(Integer tipoDosimetroId, Integer tipoPortaId, String estado) {
        String estadoFinal = (estado == null || estado.isBlank()) ? null : estado;
        List<Dosimetro> dosimetros = dosimetroRepository.filtrar(tipoDosimetroId, tipoPortaId, null, estadoFinal);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock");
            String[] cabeceras = {"Número", "Tipo", "Porta", "Tarea", "Bandeja", "Slot", "Estado", "Observación"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cabeceras.length; i++) {
                header.createCell(i).setCellValue(cabeceras[i]);
            }

            int fila = 1;
            for (Dosimetro d : dosimetros) {
                Row row = sheet.createRow(fila++);
                row.createCell(0).setCellValue(d.getNumero() != null ? d.getNumero() : 0);
                row.createCell(1).setCellValue(d.getTipoDosimetro() != null ? d.getTipoDosimetro().getNombre() : "");
                row.createCell(2).setCellValue(d.getTipoPorta() != null ? d.getTipoPorta().getNombre() : "");
                row.createCell(3).setCellValue(d.getTarea() != null ? d.getTarea().getNumeroTarea() : "");
                if (d.getNumeroBandeja() != null) row.createCell(4).setCellValue(d.getNumeroBandeja());
                if (d.getSlotBandeja() != null) row.createCell(5).setCellValue(d.getSlotBandeja());
                row.createCell(6).setCellValue(d.getEstado() != null ? d.getEstado() : "");
                row.createCell(7).setCellValue(d.getObservacion() != null ? d.getObservacion() : "");
            }
            for (int i = 0; i < cabeceras.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar el Excel de stock", e);
        }
    }

    public List<DosimetroResponse> buscarPorNumero(Integer numero) {
        return dosimetroRepository.findByNumeroOrderByIdAsc(numero)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Buscar: solo dosímetros que ya fueron asignados (tienen historial de asignación).
    public List<DosimetroDetalleResponse> buscarDetallePorNumero(Integer numero) {
        List<Dosimetro> dosimetros = dosimetroRepository.findByNumeroConAsignacion(numero);
        if (dosimetros.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No se encontraron dosímetros asignados con número: " + numero);
        }
        return dosimetros.stream().map(this::toDetalleResponse).toList();
    }

    // #14 (Edición): TODOS los dosímetros con un número (con o sin asignación).
    public List<DosimetroDetalleResponse> detalleTodosPorNumero(Integer numero) {
        List<Dosimetro> dosimetros = dosimetroRepository.findByNumeroOrderByIdAsc(numero);
        if (dosimetros.isEmpty()) {
            throw new ResourceNotFoundException("No se encontraron dosímetros con número: " + numero);
        }
        return dosimetros.stream().map(this::toDetalleResponse).toList();
    }

    /**
     * #14 (Edición): actualiza las especificaciones del dosímetro (número, tipo,
     * porta, observación) sin tocar tarea, bandeja ni slot.
     */
    @Transactional
    public DosimetroResponse editarEspecificaciones(Integer id, EditarEspecificacionesRequest request) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));

        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findById(request.getTipoDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de dosímetro no encontrado con id: " + request.getTipoDosimetroId()));

        TipoPorta tipoPorta = resolverTipoPorta(request.getTipoPortaId(), tipoDosimetro);

        dosimetro.setNumero(request.getNumero());
        dosimetro.setTipoDosimetro(tipoDosimetro);
        dosimetro.setTipoPorta(tipoPorta);
        dosimetro.setObservacion(request.getObservacion());
        // tarea, bandeja, slot y estado se conservan.

        return toResponse(dosimetroRepository.save(dosimetro));
    }

    public DosimetroResponse obtenerPorId(Integer id) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        return toResponse(dosimetro);
    }

    public DosimetroResponse crear(DosimetroRequest request) {
        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findById(request.getTipoDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de dosímetro no encontrado con id: " + request.getTipoDosimetroId()));

        TipoPorta tipoPorta = resolverTipoPorta(request.getTipoPortaId(), tipoDosimetro);
        Tarea tarea = resolverTarea(request, tipoDosimetro);

        Dosimetro dosimetro = new Dosimetro();
        dosimetro.setNumero(request.getNumero());
        dosimetro.setTipoDosimetro(tipoDosimetro);
        dosimetro.setTipoPorta(tipoPorta);
        dosimetro.setTarea(tarea);
        dosimetro.setNumeroBandeja(request.getNumeroBandeja());
        dosimetro.setSlotBandeja(request.getSlotBandeja());
        dosimetro.setEstado(request.getEstado() != null ? request.getEstado() : "disponible");
        dosimetro.setObservacion(request.getObservacion());
        dosimetro.setFechaCreacion(
                tarea != null ? tarea.getFechaCreacion() : java.time.LocalDate.now());

        return toResponse(dosimetroRepository.save(dosimetro));
    }

    public DosimetroResponse actualizar(Integer id, DosimetroRequest request) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));

        TipoDosimetro tipoDosimetro = tipoDosimetroRepository.findById(request.getTipoDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de dosímetro no encontrado con id: " + request.getTipoDosimetroId()));

        TipoPorta tipoPorta = resolverTipoPorta(request.getTipoPortaId(), tipoDosimetro);
        Tarea tarea = resolverTarea(request, tipoDosimetro);

        dosimetro.setNumero(request.getNumero());
        dosimetro.setTipoDosimetro(tipoDosimetro);
        dosimetro.setTipoPorta(tipoPorta);
        dosimetro.setTarea(tarea);
        dosimetro.setNumeroBandeja(request.getNumeroBandeja());
        dosimetro.setSlotBandeja(request.getSlotBandeja());
        dosimetro.setEstado(request.getEstado() != null ? request.getEstado() : dosimetro.getEstado());
        dosimetro.setObservacion(request.getObservacion());

        return toResponse(dosimetroRepository.save(dosimetro));
    }

    @Transactional
    public void liberar(Integer id) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        if ("baja".equalsIgnoreCase(dosimetro.getEstado())) {
            throw new IllegalArgumentException("No se puede liberar un dosímetro dado de baja");
        }
        dosimetro.setEstado("disponible");
        dosimetroRepository.save(dosimetro);
    }

    // HU #9: lista los dosímetros con número repetido, agrupados por número.
    public List<DuplicadoResponse> listarDuplicados() {
        Map<Integer, List<DosimetroResponse>> porNumero = new LinkedHashMap<>();
        for (Dosimetro d : dosimetroRepository.findDuplicados()) {
            porNumero.computeIfAbsent(d.getNumero(), k -> new ArrayList<>()).add(toResponse(d));
        }
        return porNumero.entrySet().stream()
                .map(e -> new DuplicadoResponse(e.getKey(), e.getValue().size(), e.getValue()))
                .toList();
    }

    // HU #9: marca un dosímetro como stock de emergencia en su observación.
    public DosimetroResponse marcarStockEmergencia(Integer id, String observacion) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        String obs = (observacion == null || observacion.isBlank()) ? "stock emergencia" : observacion;
        dosimetro.setObservacion(obs);
        return toResponse(dosimetroRepository.save(dosimetro));
    }

    // Marca un dosímetro como dañado (no podrá asignarse). Debe estar libre.
    public DosimetroResponse marcarDanado(Integer id) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        if ("asignado".equalsIgnoreCase(dosimetro.getEstado())) {
            throw new IllegalArgumentException(
                    "El dosímetro está asignado; libéralo antes de marcarlo como dañado");
        }
        dosimetro.setEstado("dañado");
        return toResponse(dosimetroRepository.save(dosimetro));
    }

    // Marca como bueno un dosímetro dañado (vuelve a disponible).
    public DosimetroResponse marcarBueno(Integer id) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        if (!"dañado".equalsIgnoreCase(dosimetro.getEstado())) {
            throw new IllegalArgumentException("El dosímetro no está marcado como dañado");
        }
        dosimetro.setEstado("disponible");
        return toResponse(dosimetroRepository.save(dosimetro));
    }

    public void darDeBaja(Integer id, String observacion) {
        Dosimetro dosimetro = dosimetroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosímetro no encontrado con id: " + id));
        dosimetro.setEstado("baja");
        dosimetro.setObservacion(observacion);
        dosimetroRepository.save(dosimetro);
    }

    /**
     * Lista las tareas con su conteo de dosímetros (total y disponibles) y si son
     * eliminables. Una tarea es eliminable solo si TODOS sus dosímetros están
     * disponibles y ninguno tiene historial de asignación (protege datos reales).
     */
    public List<TareaEliminableResponse> listarTareasEliminables() {
        // Tareas donde se hizo alguna asignación: NO se pueden borrar (perderían
        // ese dato del historial). El historial pasado del dosímetro no importa,
        // porque al eliminar la tarea el dosímetro se conserva (solo se desarma).
        Set<Integer> conAsignaciones = new HashSet<>(asignacionRepository.tareaIdsConAsignaciones());

        List<TareaEliminableResponse> out = new ArrayList<>();
        for (Object[] r : dosimetroRepository.conteoDosimetrosPorTarea()) {
            Integer tareaId = (Integer) r[0];
            String numero = (String) r[1];
            int total = ((Number) r[2]).intValue();
            int disponibles = ((Number) r[3]).intValue();
            boolean todosDisponibles = disponibles == total;
            boolean tieneAsignaciones = conAsignaciones.contains(tareaId);
            boolean eliminable = todosDisponibles && !tieneAsignaciones;
            String motivo = null;
            if (tieneAsignaciones) {
                motivo = "Se hicieron asignaciones en esta tarea";
            } else if (!todosDisponibles) {
                motivo = "Tiene " + (total - disponibles) + " dosímetro(s) asignados";
            }
            out.add(new TareaEliminableResponse(tareaId, numero, total, disponibles, eliminable, motivo));
        }
        return out;
    }

    /**
     * Elimina las tareas indicadas (la agrupación/armado), CONSERVANDO los
     * dosímetros y su historial: sus dosímetros se "desarman" (tarea, bandeja y
     * slot quedan vacíos) y siguen disponibles. Valida TODAS antes de aplicar
     * (todo o nada): solo procede si en la tarea no se hizo ninguna asignación y
     * todos sus dosímetros están disponibles.
     */
    @Transactional
    public EliminarTareasResponse eliminarTareas(List<Integer> tareaIds) {
        if (tareaIds == null || tareaIds.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar al menos una tarea");
        }
        // 1) Validación previa de TODAS las tareas (todo o nada).
        for (Integer id : tareaIds) {
            Tarea t = tareaRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con id: " + id));
            boolean todosDisponibles = dosimetroRepository.findByTareaId(id).stream()
                    .allMatch(d -> "disponible".equalsIgnoreCase(d.getEstado()));
            long asignacionesEnTarea = asignacionRepository.contarAsignacionesEnTarea(id);
            if (asignacionesEnTarea > 0) {
                throw new IllegalArgumentException("La tarea " + t.getNumeroTarea()
                        + " no se puede eliminar: se hicieron asignaciones en ella."
                        + " No se eliminó ninguna.");
            }
            if (!todosDisponibles) {
                throw new IllegalArgumentException("La tarea " + t.getNumeroTarea()
                        + " no se puede eliminar: tiene dosímetros asignados."
                        + " No se eliminó ninguna.");
            }
        }
        // 2) Desarmar los dosímetros (conservándolos) y luego borrar la tarea.
        int tareas = 0;
        int dosimetros = 0;
        for (Integer id : tareaIds) {
            List<Dosimetro> ds = dosimetroRepository.findByTareaId(id);
            for (Dosimetro d : ds) {
                d.setTarea(null);
                d.setNumeroBandeja(null);
                d.setSlotBandeja(null);
                // El dosímetro queda disponible / sin armar; su historial se mantiene.
            }
            dosimetroRepository.saveAll(ds);
            dosimetroRepository.flush(); // aplica el desarmado antes de borrar la tarea (FK)
            dosimetros += ds.size();
            tareaRepository.deleteById(id);
            tareas++;
        }
        return new EliminarTareasResponse(tareas, dosimetros);
    }

    /**
     * HU #6 — Actualiza el tipo de porta de todos los dosímetros de una tarea
     * que caigan dentro del rango de bandeja y slot indicados.
     */
    @Transactional
    public ActualizarTipoPortaRangoResponse actualizarTipoPortaPorRango(
            ActualizarTipoPortaRangoRequest request) {

        if (request.getBandejaDesde() > request.getBandejaHasta()) {
            throw new IllegalArgumentException("bandejaDesde no puede ser mayor que bandejaHasta");
        }
        if (request.getSlotDesde() != null && request.getSlotHasta() != null
                && request.getSlotDesde() > request.getSlotHasta()) {
            throw new IllegalArgumentException("slotDesde no puede ser mayor que slotHasta");
        }

        TipoPorta tipoPorta = tipoPortaRepository.findById(request.getTipoPortaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de porta no encontrado con id: " + request.getTipoPortaId()));

        List<Dosimetro> dosimetros = dosimetroRepository.findByTareaYRangoBandejaSlot(
                request.getTareaId(),
                request.getBandejaDesde(),
                request.getBandejaHasta(),
                request.getSlotDesde(),
                request.getSlotHasta()
        );

        for (Dosimetro d : dosimetros) {
            // Validar compatibilidad porta/dosimetro
            if (!tipoPorta.getTipoDosimetro().getId().equals(d.getTipoDosimetro().getId())) {
                throw new IllegalArgumentException(
                        "El tipo de porta '" + tipoPorta.getNombre() +
                        "' no es compatible con el dosímetro #" + d.getNumero() +
                        " (tipo: " + d.getTipoDosimetro().getNombre() + ")");
            }
            d.setTipoPorta(tipoPorta);
        }

        dosimetroRepository.saveAll(dosimetros);
        return new ActualizarTipoPortaRangoResponse(dosimetros.size());
    }

    // #13 (individual): dosímetros disponibles con un número dado (para asignar uno concreto).
    public List<DosimetroResponse> disponiblesPorNumero(Integer numero) {
        return dosimetroRepository.findByNumeroOrderByIdAsc(numero).stream()
                .filter(d -> "disponible".equalsIgnoreCase(d.getEstado()))
                .map(this::toResponse)
                .toList();
    }

    // #7: resumen de armado de todas las tareas (armadas / parciales / sin armar).
    public List<TareaArmadoResponse> resumenArmadoPorTarea() {
        return dosimetroRepository.resumenArmadoPorTarea().stream()
                .map(o -> new TareaArmadoResponse(
                        (Integer) o[0],
                        (String) o[1],
                        ((Number) o[2]).longValue(),
                        o[3] == null ? 0L : ((Number) o[3]).longValue()))
                .toList();
    }

    // #7: dosímetros de una tarea (mapa de bandejas/slots con estado de armado).
    public List<DosimetroResponse> dosimetrosDeTarea(Integer tareaId) {
        return dosimetroRepository.findByTareaIdOrderByNumeroBandejaAscSlotBandejaAsc(tareaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * #7 (modo preciso) — Arma una selección concreta de dosímetros: les asigna
     * el tipo de porta indicado, validando compatibilidad con cada uno.
     */
    @Transactional
    public ActualizarTipoPortaRangoResponse armarSeleccion(List<Integer> dosimetroIds, Integer tipoPortaId) {
        TipoPorta tipoPorta = tipoPortaRepository.findById(tipoPortaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de porta no encontrado con id: " + tipoPortaId));

        List<Dosimetro> dosimetros = dosimetroRepository.findAllById(dosimetroIds);
        for (Dosimetro d : dosimetros) {
            if (!tipoPorta.getTipoDosimetro().getId().equals(d.getTipoDosimetro().getId())) {
                throw new IllegalArgumentException(
                        "El tipo de porta '" + tipoPorta.getNombre() +
                        "' no es compatible con el dosímetro #" + d.getNumero() +
                        " (tipo: " + d.getTipoDosimetro().getNombre() + ")");
            }
            d.setTipoPorta(tipoPorta);
        }
        dosimetroRepository.saveAll(dosimetros);
        return new ActualizarTipoPortaRangoResponse(dosimetros.size());
    }

    // --- helpers privados ---

    private TipoPorta resolverTipoPorta(Integer tipoPortaId, TipoDosimetro tipoDosimetro) {
        if (tipoPortaId == null) return null;
        TipoPorta tp = tipoPortaRepository.findById(tipoPortaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de porta no encontrado con id: " + tipoPortaId));
        if (!tp.getTipoDosimetro().getId().equals(tipoDosimetro.getId())) {
            throw new IllegalArgumentException("El tipo de porta no es compatible con el tipo de dosímetro");
        }
        return tp;
    }

    private Tarea resolverTarea(DosimetroRequest request, TipoDosimetro tipoDosimetro) {
        if ("OSL".equalsIgnoreCase(tipoDosimetro.getNombre())) {
            request.setTareaId(null);
            request.setNumeroBandeja(null);
            request.setSlotBandeja(null);
            return null;
        }
        if (request.getTareaId() == null) return null;
        return tareaRepository.findById(request.getTareaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tarea no encontrada con id: " + request.getTareaId()));
    }

    private DosimetroResponse toResponse(Dosimetro d) {
        return new DosimetroResponse(
                d.getId(),
                d.getNumero(),
                d.getTipoDosimetro().getId(),
                d.getTipoDosimetro().getNombre(),
                d.getTipoPorta() != null ? d.getTipoPorta().getId() : null,
                d.getTipoPorta() != null ? d.getTipoPorta().getNombre() : null,
                d.getTarea() != null ? d.getTarea().getId() : null,
                d.getTarea() != null ? d.getTarea().getNumeroTarea() : null,
                d.getNumeroBandeja(),
                d.getSlotBandeja(),
                d.getEstado(),
                d.getObservacion()
        );
    }

    private DosimetroDetalleResponse toDetalleResponse(Dosimetro d) {
        List<AsignacionResponse> asignaciones = asignacionRepository
                .findByDosimetroIdOrderByFechaAsignacionDesc(d.getId())
                .stream()
                .map(this::toAsignacionResponse)
                .toList();

        return new DosimetroDetalleResponse(
                d.getId(),
                d.getNumero(),
                d.getTipoDosimetro().getId(),
                d.getTipoDosimetro().getNombre(),
                d.getTipoPorta() != null ? d.getTipoPorta().getId() : null,
                d.getTipoPorta() != null ? d.getTipoPorta().getNombre() : null,
                d.getTarea() != null ? d.getTarea().getId() : null,
                d.getTarea() != null ? d.getTarea().getNumeroTarea() : null,
                d.getNumeroBandeja(),
                d.getSlotBandeja(),
                d.getEstado(),
                d.getObservacion(),
                asignaciones
        );
    }

    private AsignacionResponse toAsignacionResponse(Asignacion a) {
        return new AsignacionResponse(
                a.getId(),
                a.getDosimetro().getId(),
                a.getDosimetro().getNumero(),
                a.getCliente().getId(),
                a.getCliente().getRazonSocial(),
                a.getEjecutivo().getId(),
                a.getEjecutivo().getNombre(),
                a.getEmpresa().getId(),
                a.getEmpresa().getNombre(),
                a.getTipoPorta().getId(),
                a.getTipoPorta().getNombre(),
                a.getTarea() != null ? a.getTarea().getId() : null,
                a.getTarea() != null ? a.getTarea().getNumeroTarea() : null,
                a.getNumeroBandeja(),
                a.getSlotBandeja(),
                a.getTrimestre(),
                a.getFechaAsignacion(),
                a.getLinkTrello()
        );
    }
}
