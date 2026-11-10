package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.ClienteRequestDTO;
import br.edu.ifc.bikes.dto.ClienteResponseDTO;
import br.edu.ifc.bikes.dto.mapper.ClienteMapper;
import br.edu.ifc.bikes.entity.Cliente;
import br.edu.ifc.bikes.entity.Usuario;
import br.edu.ifc.bikes.exception.ClienteUniqueViolationException;
import br.edu.ifc.bikes.exception.EntityNotFoundException;
import br.edu.ifc.bikes.repository.ClienteRepository;
import br.edu.ifc.bikes.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    public ClienteResponseDTO create(ClienteRequestDTO clienteRequestDTO, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Usuario com id:%s nao encontrado", usuarioId))
        );

        String cpf = clienteRequestDTO.cpf().replaceAll("\\D", "");

        if (clienteRepository.existsByUsuarioId(usuarioId)) {
            throw new ClienteUniqueViolationException("O usuário já possui um cliente cadastrado");
        }
        if (clienteRepository.existsByCpf(cpf)) {
            throw new ClienteUniqueViolationException(String.format("O CPF '%s' já está cadastrado", cpf));
        }

        Cliente cliente = clienteMapper.toCliente(clienteRequestDTO);
        cliente.setCpf(cpf);
        cliente.setUsuario(usuario);
        try {
            return clienteMapper.toResponse(clienteRepository.save(cliente));
        } catch (DataIntegrityViolationException ex) {
            throw new ClienteUniqueViolationException("CPF ou usuário já cadastrado como cliente");
        }
    }

    @Transactional
    public ClienteResponseDTO getById(Long id) {
        return clienteMapper.toResponse(clienteRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException(String.format("Cliente com id:%s nao encontrado", id))
        ));
    }

    @Transactional
    public List<ClienteResponseDTO> getAll() {
        return clienteMapper.toResponse(clienteRepository.findAll());
    }

    public boolean pertenceAoUsuario(Long clienteId, Long usuarioId) {
        return clienteRepository.existsByIdAndUsuarioId(clienteId, usuarioId);
    }
}
