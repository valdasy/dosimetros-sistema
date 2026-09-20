package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.asignacion.AsignacionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.AsignacionMasivaResponse;
import com.dosimetros.backend.dto.asignacion.AsignacionRequest;
import com.dosimetros.backend.dto.asignacion.AsignacionResponse;
import com.dosimetros.backend.dto.asignacion.ConteoClienteTrimestreResponse;
import com.dosimetros.backend.dto.asignacion.ConteoClienteTrimestrePortaResponse;
import com.dosimetros.backend.dto.asignacion.CorreccionExcelResponse;
import com.dosimetros.backend.dto.asignacion.CorreccionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.EditarAsignacionRequest;
import com.dosimetros.backend.dto.asignacion.LiberacionMasivaRequest;
import com.dosimetros.backend.dto.asignacion.LiberacionPreviewResponse;
import com.dosimetros.backend.dto.asignacion.LoteAsignacionResponse;
import com.dosimetros.backend.dto.asignacion.MisFiltrosResponse;
import com.dosimetros.backend.dto.asignacion.OpcionResponse;
import com.dosimetros.backend.entity.Asignacion;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Dosimetro;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.entity.Empresa;
import com.dosimetros.backend.entity.TipoPorta;
import com.dosimetros.backend.exception.ResourceNotFoundException;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.ClienteRepository;
import com.dosimetros.backend.repository.DosimetroRepository;
import com.dosimetros.backend.repository.EjecutivoRepository;
import com.dosimetros.backend.repository.EmpresaRepository;
import com.dosimetros.backend.repository.TipoPortaRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AsignacionService {

    private final AsignacionRepository asignacionRepository;
    private final DosimetroRepository dosimetroRepository;
    private final ClienteRepository clienteRepository;
    private final EjecutivoRepository ejecutivoRepository;
    private final EmpresaRepository empresaRepository;
    private final TipoPortaRepository tipoPortaRepository;

    public AsignacionService(AsignacionRepository asignacionRepository,
                             DosimetroRepository dosimetroRepository,
                             ClienteRepository clienteRepository,
                             EjecutivoRepository ejecutivoRepository,
                             EmpresaRepository empresaRepository,
                             TipoPortaRepository tipoPortaRepository) {
        this.asignacionRepository = asignacionRepository;
        this.dosimetroRepository = dosimetroRepository;
        this.clienteRepository = clienteRepository;
        this.ejecutivoRepository = ejecutivoRepository;
        this.empresaRepository = empresaRepository;
        this.tipoPortaRepository = tipoPortaRepository;
    }

    public List<AsignacionResponse> listarPorDosimetro(Integer dosimetroId) {
        return asignacionRepository.findByDosimetroIdOrderByFechaAsignacionDesc(dosimetroId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // HU #3 / #15: dosímetros asignados a los clientes del ejecutivo logueado.
    public List<AsignacionResponse> listarPorEjecutivo(Integer ejecutivoId) {
        return asignacionRepository
                .findByEjecutivoIdOrderByTrimestreDescFechaAsignacionDesc(ejecutivoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Asignaciones de un cliente (para el detalle del cliente).
    public List<AsignacionResponse> listarPorCliente(Integer clienteId) {
        return asignacionRepository.findByClienteIdOrderByFechaAsignacionDesc(clienteId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Trimestres (distintos) de un cliente, para poblar los chips sin traer todas
    // las asignaciones. ejecutivoId opcional acota al ejecutivo (vista del ejecutivo).
    public List<String> trimestresDeCliente(Integer clienteId, Integer ejecutivoId) {
        return asignacionRepository.trimestresDeCliente(clienteId, ejecutivoId);
    }

    // Resumen de un cliente: total por trimestre y tipo de porta (detalle del cliente).
    public List<com.dosimetros.backend.dto.cliente.ResumenPortaTrimestreResponse>
            resumenPortaTrimestrePorCliente(Integer clienteId) {
        return asignacionRepository.resumenPortaTrimestrePorCliente(clienteId).stream()
                .map(r -> new com.dosimetros.backend.dto.cliente.ResumenPortaTrimestreResponse(
                        (String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();
    }

    // Vista ejecutivo con multifiltros (todos opcionales).
    public List<AsignacionResponse> filtrarPorEjecutivo(
            Integer ejecutivoId, Integer clienteId, String trimestre, java.time.LocalDate fecha,
            Integer tipoPortaId, Integer numeroBandeja, Integer slotBandeja, String link) {
        String tri = (trimestre == null || trimestre.isBlank()) ? null : trimestre.trim();
        String lk = (link == null || link.isBlank()) ? null : link.trim();
        return asignacionRepository.filtrarPorEjecutivo(
                        ejecutivoId, clienteId, tri, fecha, tipoPortaId, numeroBandeja, slotBandeja, lk)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Opciones disponibles para los filtros del ejecutivo.
    public MisFiltrosResponse opcionesFiltroEjecutivo(Integer ejecutivoId) {
        List<OpcionResponse> clientes = asignacionRepository.clientesDeEjecutivo(ejecutivoId).stream()
                .map(o -> new OpcionResponse((Integer) o[0], (String) o[1]))
                .toList();
        List<OpcionResponse> portas = asignacionRepository.portasDeEjecutivo(ejecutivoId).stream()
                .map(o -> new OpcionResponse((Integer) o[0], (String) o[1]))
                .toList();
        return new MisFiltrosResponse(
                asignacionRepository.trimestresDeEjecutivo(ejecutivoId), clientes, portas);
    }

    // HU #15: asignaciones del ejecutivo agrupadas por trimestre y fecha (lotes enviados).
    public List<LoteAsignacionResponse> listarLotesPorEjecutivo(Integer ejecutivoId) {
        List<AsignacionResponse> asignaciones = listarPorEjecutivo(ejecutivoId);

        Map<String, LoteAsignacionResponse> lotes = new LinkedHashMap<>();
        for (AsignacionResponse a : asignaciones) {
            String clave = a.getTrimestre() + "|" + a.getFechaAsignacion();
            LoteAsignacionResponse lote = lotes.computeIfAbsent(clave, k ->
                    new LoteAsignacionResponse(
                            a.getTrimestre(),
                            a.getFechaAsignacion(),
                            0,
                            new ArrayList<>()
                    ));
            lote.getAsignaciones().add(a);
        }

        List<LoteAsignacionResponse> resultado = new ArrayList<>(lotes.values());
        resultado.forEach(lote -> lote.setCantidad(lote.getAsignaciones().size()));
        return resultado;
    }

    @Transactional
    public AsignacionResponse crear(AsignacionRequest request) {
        // Lectura con bloqueo pesimista: evita que dos asignaciones individuales
        // concurrentes tomen el mismo dosímetro disponible y lo entreguen dos veces.
        Dosimetro dosimetro = dosimetroRepository.findByIdParaAsignar(request.getDosimetroId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dosímetro no encontrado con id: " + request.getDosimetroId()
                ));

        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado con id: " + request.getClienteId()
                ));

        Ejecutivo ejecutivo = ejecutivoRepository.findById(request.getEjecutivoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ejecutivo no encontrado con id: " + request.getEjecutivoId()
                ));

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Empresa no encontrada con id: " + request.getEmpresaId()
                ));

        TipoPorta tipoPorta = tipoPortaRepository.findById(request.getTipoPortaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de porta no encontrado con id: " + request.getTipoPortaId()
                ));

        if (!"disponible".equalsIgnoreCase(dosimetro.getEstado())) {
            throw new IllegalArgumentException("El dosímetro no está disponible para asignación");
        }

        if (!tipoPorta.getTipoDosimetro().getId().equals(dosimetro.getTipoDosimetro().getId())) {
            throw new IllegalArgumentException("El tipo de porta no es compatible con el tipo de dosímetro");
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setDosimetro(dosimetro);
        asignacion.setCliente(cliente);
        asignacion.setEjecutivo(ejecutivo);
        asignacion.setEmpresa(empresa);
        asignacion.setTipoPorta(tipoPorta);
        asignacion.setTarea(dosimetro.getTarea());
        asignacion.setNumeroBandeja(dosimetro.getNumeroBandeja());
        asignacion.setSlotBandeja(dosimetro.getSlotBandeja());
        asignacion.setTrimestre(request.getTrimestre());
        asignacion.setFechaAsignacion(
                request.getFechaAsignacion() != null ? request.getFechaAsignacion() : LocalDate.now()
        );
        asignacion.setLinkTrello(request.getLinkTrello());

        Asignacion guardada = asignacionRepository.save(asignacion);

        dosimetro.setEstado("asignado");
        dosimetroRepository.save(dosimetro);

        return toResponse(guardada);
    }

    @Transactional
    public AsignacionMasivaResponse asignarMasivo(AsignacionMasivaRequest request) {
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado con id: " + request.getClienteId()
                ));

        Ejecutivo ejecutivo = ejecutivoRepository.findById(request.getEjecutivoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ejecutivo no encontrado con id: " + request.getEjecutivoId()
                ));

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Empresa no encontrada con id: " + request.getEmpresaId()
                ));

        TipoPorta tipoPorta = tipoPortaRepository.findById(request.getTipoPortaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de porta no encontrado con id: " + request.getTipoPortaId()
                ));

        // Solo se eligen dosímetros disponibles dentro de las tareas seleccionadas
        // cuyo tipo de dosímetro sea compatible con el tipo de porta solicitado.
        List<Dosimetro> compatibles = dosimetroRepository.findDisponiblesCompatiblesEnTareas(
                request.getTareaIds(),
                tipoPorta.getTipoDosimetro().getId()
        );

        if (compatibles.size() < request.getCantidad()) {
            throw new IllegalArgumentException(
                    "Stock insuficiente: se solicitaron " + request.getCantidad()
                            + " dosímetros compatibles pero solo hay " + compatibles.size()
                            + " disponibles en las tareas seleccionadas"
            );
        }

        LocalDate fecha = request.getFechaAsignacion() != null
                ? request.getFechaAsignacion()
                : LocalDate.now();

        List<Dosimetro> seleccionados = compatibles.subList(0, request.getCantidad());
        List<AsignacionResponse> asignaciones = new ArrayList<>(seleccionados.size());

        for (Dosimetro dosimetro : seleccionados) {
            Asignacion asignacion = new Asignacion();
            asignacion.setDosimetro(dosimetro);
            asignacion.setCliente(cliente);
            asignacion.setEjecutivo(ejecutivo);
            asignacion.setEmpresa(empresa);
            asignacion.setTipoPorta(tipoPorta);
            asignacion.setTarea(dosimetro.getTarea());
            asignacion.setNumeroBandeja(dosimetro.getNumeroBandeja());
            asignacion.setSlotBandeja(dosimetro.getSlotBandeja());
            asignacion.setTrimestre(request.getTrimestre());
            asignacion.setFechaAsignacion(fecha);
            asignacion.setLinkTrello(request.getLinkTrello());

            Asignacion guardada = asignacionRepository.save(asignacion);

            dosimetro.setEstado("asignado");
            dosimetroRepository.save(dosimetro);

            asignaciones.add(toResponse(guardada));
        }

        return new AsignacionMasivaResponse(
                request.getCantidad(),
                asignaciones.size(),
                asignaciones
        );
    }

    /**
     * Vista previa de una liberación (corrección): cuántas asignaciones se
     * eliminarían y su detalle agrupado por tarea/bandeja, sin aplicar cambios.
     */
    @Transactional(readOnly = true)
    public LiberacionPreviewResponse previsualizarLiberacion(LiberacionMasivaRequest req) {
        return construirPreview(buscarParaLiberar(req));
    }

    /**
     * Aplica la liberación: BORRA los registros de asignación que coinciden con
     * los filtros (fue un error) y devuelve sus dosímetros a "disponible" (salvo
     * los dados de baja). Devuelve cuántas asignaciones se eliminaron.
     */
    @Transactional
    public int liberarMasivo(LiberacionMasivaRequest req) {
        List<Asignacion> encontradas = buscarParaLiberar(req);

        // Dosímetros involucrados (distintos), para devolverlos a disponible.
        Set<Integer> dosimetroIds = new LinkedHashSet<>();
        for (Asignacion a : encontradas) {
            dosimetroIds.add(a.getDosimetro().getId());
        }

        // 1) Borrar los registros de asignación erróneos (historial).
        asignacionRepository.deleteAll(encontradas);
        asignacionRepository.flush();

        // 2) Devolver los dosímetros a "disponible" (los dados de baja no se tocan).
        for (Integer id : dosimetroIds) {
            Dosimetro d = dosimetroRepository.findById(id).orElse(null);
            if (d != null && !"baja".equalsIgnoreCase(d.getEstado())) {
                d.setEstado("disponible");
                dosimetroRepository.save(d);
            }
        }

        return encontradas.size();
    }

    private List<Asignacion> buscarParaLiberar(LiberacionMasivaRequest req) {
        if (req.tieneRango() && !req.tieneTarea()) {
            throw new IllegalArgumentException(
                    "El rango de bandeja/slot requiere seleccionar una tarea");
        }
        clienteRepository.findById(req.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado con id: " + req.getClienteId()));
        String tareaNumero = req.tieneTarea() ? req.getTareaNumero().trim() : null;
        return asignacionRepository.paraLiberar(
                req.getClienteId(), req.getTrimestre(), tareaNumero,
                req.getDesdeBandeja(), req.getDesdeSlot(),
                req.getHastaBandeja(), req.getHastaSlot());
    }

    // Agrupa las asignaciones por (tarea, bandeja) conservando el orden y calcula
    // el rango de slots (mínimo/máximo) y la cantidad de cada grupo.
    private LiberacionPreviewResponse construirPreview(List<Asignacion> asignaciones) {
        Map<String, LiberacionPreviewResponse.Grupo> grupos = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            String tarea = a.getTarea() != null ? a.getTarea().getNumeroTarea() : "Sin tarea";
            Integer bandeja = a.getNumeroBandeja();
            Integer slot = a.getSlotBandeja();
            String clave = tarea + "||" + (bandeja == null ? "sb" : bandeja);
            LiberacionPreviewResponse.Grupo g = grupos.computeIfAbsent(clave,
                    k -> new LiberacionPreviewResponse.Grupo(tarea, bandeja, null, null, 0));
            g.setCantidad(g.getCantidad() + 1);
            if (slot != null) {
                if (g.getSlotDesde() == null || slot < g.getSlotDesde()) g.setSlotDesde(slot);
                if (g.getSlotHasta() == null || slot > g.getSlotHasta()) g.setSlotHasta(slot);
            }
        }
        return new LiberacionPreviewResponse(asignaciones.size(), new ArrayList<>(grupos.values()));
    }

    // Exporta a Excel un conjunto de asignaciones por sus ids (resumen de asignación).
    public byte[] exportarPorIds(List<Integer> ids) {
        List<AsignacionResponse> asignaciones = asignacionRepository.findAllById(ids)
                .stream().map(this::toResponse).toList();
        return exportarAsignacionesExcel(asignaciones);
    }

    // #14 (Correcciones por Excel): exporta las asignaciones indicadas con el
    // id como clave, para editarlas en Excel y volver a subirlas.
    public byte[] exportarParaCorreccion(List<Integer> ids) {
        List<Asignacion> asignaciones = asignacionRepository.findAllById(ids);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Correcciones");
            String[] cab = {"id_asignacion", "numero_dosimetro", "cliente", "ejecutivo", "empresa",
                    "tipo_porta", "trimestre", "numero_bandeja", "slot_bandeja", "link_trello"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cab.length; i++) header.createCell(i).setCellValue(cab[i]);
            int r = 1;
            for (Asignacion a : asignaciones) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(a.getId());
                if (a.getDosimetro() != null && a.getDosimetro().getNumero() != null) {
                    row.createCell(1).setCellValue(a.getDosimetro().getNumero());
                }
                row.createCell(2).setCellValue(nvl(a.getCliente() != null ? a.getCliente().getRazonSocial() : ""));
                row.createCell(3).setCellValue(nvl(a.getEjecutivo() != null ? a.getEjecutivo().getNombre() : ""));
                row.createCell(4).setCellValue(nvl(a.getEmpresa() != null ? a.getEmpresa().getNombre() : ""));
                row.createCell(5).setCellValue(nvl(a.getTipoPorta() != null ? a.getTipoPorta().getNombre() : ""));
                row.createCell(6).setCellValue(nvl(a.getTrimestre()));
                if (a.getNumeroBandeja() != null) row.createCell(7).setCellValue(a.getNumeroBandeja());
                if (a.getSlotBandeja() != null) row.createCell(8).setCellValue(a.getSlotBandeja());
                row.createCell(9).setCellValue(nvl(a.getLinkTrello()));
            }
            for (int i = 0; i < cab.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar el Excel de correcciones", e);
        }
    }

    // #14 (Correcciones por Excel): aplica las ediciones de un Excel descargado.
    // Clave = id_asignacion. Todo o nada: si hay cualquier error, no se guarda nada.
    @Transactional
    public CorreccionExcelResponse importarCorreccion(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        CorreccionExcelResponse resp = new CorreccionExcelResponse();
        List<String> errores = new ArrayList<>();
        List<Runnable> acciones = new ArrayList<>();
        int actualizadas = 0, sinCambios = 0, fallidas = 0, total = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // encabezado
                String idStr = celda(row, 0, fmt);
                // Fila vacía: sin id ni datos.
                if (idStr.isBlank() && celda(row, 2, fmt).isBlank() && celda(row, 9, fmt).isBlank()) continue;
                total++;
                int fila = row.getRowNum() + 1;
                try {
                    if (idStr.isBlank()) throw new IllegalArgumentException("falta el id_asignacion");
                    int id;
                    try { id = (int) Double.parseDouble(idStr); }
                    catch (Exception e) { throw new IllegalArgumentException("id_asignacion inválido: '" + idStr + "'"); }
                    Asignacion a = asignacionRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("no existe la asignación id " + idStr));

                    String cliNom = celda(row, 2, fmt);
                    String ejeNom = celda(row, 3, fmt);
                    String empNom = celda(row, 4, fmt);
                    String portaNom = celda(row, 5, fmt);
                    String tri = celda(row, 6, fmt).toUpperCase();
                    String bandaStr = celda(row, 7, fmt);
                    String slotStr = celda(row, 8, fmt);
                    String link = celda(row, 9, fmt);

                    final Cliente cli = unico(clienteRepository.findByRazonSocialIgnoreCaseAndActivoTrue(cliNom), "cliente", cliNom);
                    final Ejecutivo eje = unico(ejecutivoRepository.findByNombreIgnoreCaseAndActivoTrue(ejeNom), "ejecutivo", ejeNom);
                    final Empresa emp = unico(empresaRepository.findByNombreIgnoreCaseAndActivaTrue(empNom), "empresa", empNom);
                    final TipoPorta porta = tipoPortaRepository
                            .findByNombreAndTipoDosimetroId(portaNom, a.getDosimetro().getTipoDosimetro().getId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "tipo de porta '" + portaNom + "' no válido para el dosímetro #" + a.getDosimetro().getNumero()));
                    if (!tri.matches("[1-4]T\\d{4}")) throw new IllegalArgumentException("trimestre inválido: '" + tri + "'");
                    final Integer banda = bandaStr.isBlank() ? null : (int) Double.parseDouble(bandaStr);
                    final Integer slot = slotStr.isBlank() ? null : (int) Double.parseDouble(slotStr);
                    if (link.isBlank()) throw new IllegalArgumentException("el link de Trello no puede quedar vacío");

                    boolean cambia = !cli.getId().equals(a.getCliente().getId())
                            || !eje.getId().equals(a.getEjecutivo().getId())
                            || !emp.getId().equals(a.getEmpresa().getId())
                            || !porta.getId().equals(a.getTipoPorta().getId())
                            || !tri.equals(a.getTrimestre())
                            || !Objects.equals(banda, a.getNumeroBandeja())
                            || !Objects.equals(slot, a.getSlotBandeja())
                            || !link.trim().equals(a.getLinkTrello());

                    if (!cambia) {
                        sinCambios++;
                        continue;
                    }
                    final String linkFinal = link.trim();
                    acciones.add(() -> {
                        a.setCliente(cli);
                        a.setEjecutivo(eje);
                        a.setEmpresa(emp);
                        a.setTipoPorta(porta);
                        a.setTrimestre(tri);
                        a.setNumeroBandeja(banda);
                        a.setSlotBandeja(slot);
                        a.setLinkTrello(linkFinal);
                        asignacionRepository.save(a);
                    });
                    actualizadas++;
                } catch (Exception e) {
                    fallidas++;
                    errores.add("Fila " + fila + ": " + e.getMessage());
                }
            }

            // Todo o nada: solo se persiste si no hubo errores.
            if (errores.isEmpty()) {
                acciones.forEach(Runnable::run);
            } else {
                resp.setAplicado(false);
                actualizadas = 0;
                sinCambios = 0;
            }
            resp.setTotalFilas(total);
            resp.setActualizadas(actualizadas);
            resp.setSinCambios(sinCambios);
            resp.setFallidas(fallidas);
            resp.setErrores(errores);
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo Excel", e);
        }
        return resp;
    }

    private String celda(Row row, int col, DataFormatter fmt) {
        Cell c = row.getCell(col);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private <T> T unico(List<T> encontrados, String campo, String valor) {
        if (encontrados.isEmpty()) throw new IllegalArgumentException(campo + " no encontrado: '" + valor + "'");
        if (encontrados.size() > 1) throw new IllegalArgumentException(campo + " ambiguo (hay varios): '" + valor + "'");
        return encontrados.get(0);
    }

    /**
     * #15: exporta una lista de asignaciones a Excel (.xlsx). Incluye la tarea.
     */
    public byte[] exportarAsignacionesExcel(List<AsignacionResponse> asignaciones) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Asignaciones");
            String[] cab = {"N° dosímetro", "Cliente", "Ejecutivo", "Empresa", "Trimestre",
                    "Fecha asignación", "Porta", "Tarea", "Bandeja", "Slot", "Link Trello"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cab.length; i++) header.createCell(i).setCellValue(cab[i]);

            int r = 1;
            for (AsignacionResponse a : asignaciones) {
                Row row = sheet.createRow(r++);
                if (a.getNumeroDosimetro() != null) row.createCell(0).setCellValue(a.getNumeroDosimetro());
                row.createCell(1).setCellValue(nvl(a.getClienteNombre()));
                row.createCell(2).setCellValue(nvl(a.getEjecutivoNombre()));
                row.createCell(3).setCellValue(nvl(a.getEmpresaNombre()));
                row.createCell(4).setCellValue(nvl(a.getTrimestre()));
                row.createCell(5).setCellValue(a.getFechaAsignacion() != null ? a.getFechaAsignacion().toString() : "");
                row.createCell(6).setCellValue(nvl(a.getTipoPortaNombre()));
                row.createCell(7).setCellValue(nvl(a.getNumeroTarea()));
                if (a.getNumeroBandeja() != null) row.createCell(8).setCellValue(a.getNumeroBandeja());
                if (a.getSlotBandeja() != null) row.createCell(9).setCellValue(a.getSlotBandeja());
                row.createCell(10).setCellValue(nvl(a.getLinkTrello()));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar el Excel de asignaciones", e);
        }
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    // #18: conteo de asignaciones por cliente y trimestre (comparación por trimestres).
    public List<ConteoClienteTrimestreResponse> conteoPorClienteTrimestre(Integer ejecutivoId) {
        return asignacionRepository.conteoPorClienteTrimestre(ejecutivoId).stream()
                .map(o -> new ConteoClienteTrimestreResponse(
                        (Integer) o[0], (String) o[1], (String) o[2], (String) o[3], ((Number) o[4]).longValue()))
                .toList();
    }

    // Conteo por cliente, trimestre y tipo de porta (para desglosar por porta los
    // pendientes de asignación).
    public List<ConteoClienteTrimestrePortaResponse> conteoPorClienteTrimestrePorta(Integer ejecutivoId) {
        return asignacionRepository.conteoPorClienteTrimestrePorta(ejecutivoId).stream()
                .map(o -> new ConteoClienteTrimestrePortaResponse(
                        (Integer) o[0], (String) o[1], (Integer) o[2], (String) o[3], ((Number) o[4]).longValue()))
                .toList();
    }

    // #18: asignaciones por estado de envío (enviado=false => pendientes).
    public List<AsignacionResponse> porEstadoEnvio(Integer ejecutivoId, String trimestre, boolean enviado) {
        String tri = (trimestre == null || trimestre.isBlank()) ? null : trimestre.trim();
        return asignacionRepository.porEstadoEnvio(ejecutivoId, tri, enviado)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // #18: marca un conjunto de asignaciones como enviadas (o revierte).
    @Transactional
    public int marcarEnvio(List<Integer> asignacionIds, boolean enviado) {
        List<Asignacion> asignaciones = asignacionRepository.findAllById(asignacionIds);
        for (Asignacion a : asignaciones) {
            a.setEnviado(enviado);
            a.setFechaEnvio(enviado ? LocalDate.now() : null);
        }
        asignacionRepository.saveAll(asignaciones);
        return asignaciones.size();
    }

    // #14: edita una asignación existente del historial (incluido el cliente).
    @Transactional
    public AsignacionResponse editarAsignacion(Integer id, EditarAsignacionRequest req) {
        Asignacion a = asignacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asignación no encontrada con id: " + id));
        Cliente cliente = clienteRepository.findById(req.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + req.getClienteId()));
        Ejecutivo ejecutivo = ejecutivoRepository.findById(req.getEjecutivoId())
                .orElseThrow(() -> new ResourceNotFoundException("Ejecutivo no encontrado con id: " + req.getEjecutivoId()));
        Empresa empresa = empresaRepository.findById(req.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + req.getEmpresaId()));
        TipoPorta tipoPorta = tipoPortaRepository.findById(req.getTipoPortaId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + req.getTipoPortaId()));

        if (!tipoPorta.getTipoDosimetro().getId().equals(a.getDosimetro().getTipoDosimetro().getId())) {
            throw new IllegalArgumentException("El tipo de porta '" + tipoPorta.getNombre()
                    + "' no es compatible con el dosímetro " + a.getDosimetro().getNumero());
        }

        a.setCliente(cliente);
        a.setEjecutivo(ejecutivo);
        a.setEmpresa(empresa);
        a.setTipoPorta(tipoPorta);
        a.setTrimestre(req.getTrimestre());
        a.setLinkTrello(req.getLinkTrello());
        return toResponse(asignacionRepository.save(a));
    }

    // #14 (Correcciones): busca asignaciones con filtros (admin) para corregir en lote.
    public List<AsignacionResponse> buscarAsignaciones(Integer clienteId, Integer ejecutivoId,
                                                       Integer empresaId, String trimestre,
                                                       Integer tipoPortaId, String link) {
        String tri = (trimestre == null || trimestre.isBlank()) ? null : trimestre.trim();
        String lnk = (link == null || link.isBlank()) ? null : link.trim();
        return asignacionRepository
                .filtrarAsignaciones(clienteId, ejecutivoId, empresaId, tri, tipoPortaId, lnk)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * #14 (Correcciones): cambia un mismo campo en varias asignaciones a la vez.
     * campo ∈ {linkTrello, clienteId, ejecutivoId, empresaId, tipoPortaId}.
     */
    @Transactional
    public int correccionMasiva(CorreccionMasivaRequest request) {
        List<Asignacion> asignaciones = asignacionRepository.findAllById(request.getAsignacionIds());
        String campo = request.getCampo();
        String valor = request.getValor();

        // Resolver la entidad destino una sola vez cuando aplica.
        Cliente cliente = null;
        Ejecutivo ejecutivo = null;
        Empresa empresa = null;
        TipoPorta tipoPorta = null;
        switch (campo) {
            case "linkTrello" -> {
                if (valor == null || valor.isBlank()) {
                    throw new IllegalArgumentException("El link de Trello no puede quedar vacío");
                }
            }
            case "clienteId" -> cliente = clienteRepository.findById(Integer.valueOf(valor))
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + valor));
            case "ejecutivoId" -> ejecutivo = ejecutivoRepository.findById(Integer.valueOf(valor))
                    .orElseThrow(() -> new ResourceNotFoundException("Ejecutivo no encontrado con id: " + valor));
            case "empresaId" -> empresa = empresaRepository.findById(Integer.valueOf(valor))
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + valor));
            case "tipoPortaId" -> tipoPorta = tipoPortaRepository.findById(Integer.valueOf(valor))
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de porta no encontrado con id: " + valor));
            case "trimestre" -> {
                if (valor == null || !valor.trim().toUpperCase().matches("[1-4]T\\d{4}")) {
                    throw new IllegalArgumentException("El trimestre debe tener el formato 1T2026, 2T2026, etc.");
                }
            }
            case "numeroBandeja", "slotBandeja" -> {
                try { Integer.parseInt(valor.trim()); }
                catch (Exception e) { throw new IllegalArgumentException("El valor debe ser un número entero"); }
            }
            case "fechaAsignacion" -> {
                try { LocalDate.parse(valor.trim()); }
                catch (Exception e) { throw new IllegalArgumentException("La fecha debe tener formato AAAA-MM-DD"); }
            }
            default -> throw new IllegalArgumentException("Campo no permitido para corrección: " + campo);
        }

        for (Asignacion a : asignaciones) {
            switch (campo) {
                case "linkTrello" -> a.setLinkTrello(valor.trim());
                case "clienteId" -> a.setCliente(cliente);
                case "ejecutivoId" -> a.setEjecutivo(ejecutivo);
                case "empresaId" -> a.setEmpresa(empresa);
                case "tipoPortaId" -> {
                    if (!tipoPorta.getTipoDosimetro().getId()
                            .equals(a.getDosimetro().getTipoDosimetro().getId())) {
                        throw new IllegalArgumentException(
                                "El tipo de porta '" + tipoPorta.getNombre()
                                        + "' no es compatible con el dosímetro #" + a.getDosimetro().getNumero());
                    }
                    a.setTipoPorta(tipoPorta);
                }
                case "trimestre" -> a.setTrimestre(valor.trim().toUpperCase());
                case "numeroBandeja" -> a.setNumeroBandeja(Integer.parseInt(valor.trim()));
                case "slotBandeja" -> a.setSlotBandeja(Integer.parseInt(valor.trim()));
                case "fechaAsignacion" -> a.setFechaAsignacion(LocalDate.parse(valor.trim()));
            }
        }
        asignacionRepository.saveAll(asignaciones);
        return asignaciones.size();
    }

    private AsignacionResponse toResponse(Asignacion a) {
        AsignacionResponse r = new AsignacionResponse(
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
        r.setClienteNombreCorto(a.getCliente().getNombreCorto());
        r.setEnviado(a.isEnviado());
        r.setFechaEnvio(a.getFechaEnvio());
        return r;
    }
}