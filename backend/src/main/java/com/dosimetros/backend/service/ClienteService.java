package com.dosimetros.backend.service;

import com.dosimetros.backend.dto.cliente.ClienteRequest;
import com.dosimetros.backend.dto.cliente.ClienteResponse;
import com.dosimetros.backend.entity.Cliente;
import com.dosimetros.backend.entity.Ejecutivo;
import com.dosimetros.backend.exception.ResourceNotFoundException;
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

    public ClienteService(ClienteRepository clienteRepository,
                          EjecutivoRepository ejecutivoRepository) {
        this.clienteRepository = clienteRepository;
        this.ejecutivoRepository = ejecutivoRepository;
    }

    public List<ClienteResponse> listarActivos() {
        Set<Integer> conDosimetros = clientesConDosimetroVigente();
        return clienteRepository.findByActivoTrue()
                .stream()
                .map(c -> toResponse(c, conDosimetros))
                .toList();
    }

    // #16: clientes activos filtrados por ejecutivo, empresa y/o texto.
    public List<ClienteResponse> filtrar(Integer ejecutivoId, Integer empresaId, String q) {
        String qq = (q == null || q.isBlank()) ? null : q.trim();
        Set<Integer> conDosimetros = clientesConDosimetroVigente();
        return clienteRepository.filtrar(ejecutivoId, empresaId, qq)
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
