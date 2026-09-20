package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.cliente.ClienteRequest;
import com.dosimetros.backend.dto.cliente.ClienteResponse;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.exception.ResourceNotFoundException;
import com.dosimetros.backend.repository.AsignacionRepository;
import com.dosimetros.backend.repository.ClienteRepository;
import com.dosimetros.backend.repository.EjecutivoRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EjecutivoRepository ejecutivoRepository;
    private final AsignacionRepository asignacionRepository;

    public ClienteService(ClienteRepository clienteRepository,
                          EjecutivoRepository ejecutivoRepository,
                          AsignacionRepository asignacionRepository) {
        this.clienteRepository = clienteRepository;
        this.ejecutivoRepository = ejecutivoRepository;
        this.asignacionRepository = asignacionRepository;
    }

    public List<ClienteResponse> listarActivos() {
        Set<Integer> conDosimetros = clientesConDosimetroVigente();
        return clienteRepository.findByActivoTrue()
                .stream()
                .map(c -> toResponse(c, conDosimetros))
                .toList();
    }

    // #16: clientes filtrados por ejecutivo, empresa y/o texto. incluirInactivos
    // muestra también los dados de baja (para poder reactivarlos).
    public List<ClienteResponse> filtrar(Integer ejecutivoId, Integer empresaId, String q,
                                         boolean incluirInactivos) {
        String qq = (q == null || q.isBlank()) ? null : q.trim();
        Set<Integer> conDosimetros = clientesConDosimetroVigente();
        return clienteRepository.filtrar(ejecutivoId, empresaId, qq, incluirInactivos)
                .stream()
                .map(c -> toResponse(c, conDosimetros))
                .toList();
    }

    // HU #3 / #16: clientes cuyo ejecutivo responsable es el logueado.
    public List<ClienteResponse> listarPorEjecutivo(Integer ejecutivoId) {
        Set<Integer> conDosimetros = clientesConDosimetroVigente();
        return clienteRepository.findByEjecutivoIdAndActivoTrueOrderByRazonSocialAsc(ejecutivoId)
                .stream()
                .map(c -> toResponse(c, conDosimetros))
                .toList();
    }

    public ClienteResponse obtenerPorId(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));
        return toResponse(cliente, clientesConDosimetroVigente());
    }

    public ClienteResponse crear(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial(request.getRazonSocial());
        cliente.setNombreCorto(request.getNombreCorto());
        cliente.setRut(request.getRut());
        cliente.setEjecutivo(resolverEjecutivo(request.getEjecutivoId()));
        cliente.setActivo(true);

        return toResponse(clienteRepository.save(cliente), clientesConDosimetroVigente());
    }

    public ClienteResponse actualizar(Integer id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));

        cliente.setRazonSocial(request.getRazonSocial());
        cliente.setNombreCorto(request.getNombreCorto());
        cliente.setRut(request.getRut());
        cliente.setEjecutivo(resolverEjecutivo(request.getEjecutivoId()));

        return toResponse(clienteRepository.save(cliente), clientesConDosimetroVigente());
    }

    public void desactivar(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));

        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }

    // Reactiva un cliente dado de baja (vuelve a los listados activos).
    public void reactivar(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));

        cliente.setActivo(true);
        clienteRepository.save(cliente);
    }

    // Cuántas asignaciones (histórico) tiene el cliente. Sirve para decidir si se
    // puede eliminar físicamente (solo si no tiene historial).
    public long contarAsignaciones(Integer id) {
        clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));
        return asignacionRepository.countByClienteId(id);
    }

    // Borrado FÍSICO: solo para clientes creados por error (sin ninguna
    // asignación). Si tiene historial, se bloquea y debe usarse la baja lógica
    // para no perder datos.
    public void eliminar(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));

        long asignaciones = asignacionRepository.countByClienteId(id);
        if (asignaciones > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el cliente '" + cliente.getRazonSocial() + "': tiene " +
                    asignaciones + " asignación(es) en el histórico. Usa 'Desactivar' para darlo de baja " +
                    "sin perder el historial.");
        }
        clienteRepository.delete(cliente);
    }

    private Ejecutivo resolverEjecutivo(Integer ejecutivoId) {
        if (ejecutivoId == null) return null;
        return ejecutivoRepository.findById(ejecutivoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ejecutivo no encontrado con id: " + ejecutivoId));
    }

    private Set<Integer> clientesConDosimetroVigente() {
        return new HashSet<>(clienteRepository.findIdsClientesConDosimetroVigente());
    }

    private ClienteResponse toResponse(Cliente cliente, Set<Integer> conDosimetros) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getRazonSocial(),
                cliente.getNombreCorto(),
                cliente.getRut(),
                cliente.getActivo(),
                cliente.getEjecutivo() != null ? cliente.getEjecutivo().getId() : null,
                cliente.getEjecutivo() != null ? cliente.getEjecutivo().getNombre() : null,
                !conDosimetros.contains(cliente.getId())
        );
    }
}
