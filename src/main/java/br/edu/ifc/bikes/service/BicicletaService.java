package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.BicicletaRequestDTO;
import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.dto.mapper.BicicletaMapper;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.exception.CodigoUniqueViolationException;
import br.edu.ifc.bikes.exception.EntityNotFoundException;
import br.edu.ifc.bikes.exception.RegraDeNegocioException;
import br.edu.ifc.bikes.repository.BicicletaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BicicletaService {

    private final BicicletaRepository bicicletaRepository;
    private final BicicletaMapper bicicletaMapper;

    @Transactional
    public BicicletaResponseDTO create(BicicletaRequestDTO bicicletaRequestDTO) {
        String codigo = normalizarCodigo(bicicletaRequestDTO.codigo());
        if (bicicletaRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new CodigoUniqueViolationException(String.format("O código '%s' já está cadastrado", codigo));
        }

        Bicicleta bicicleta = bicicletaMapper.toBicicleta(bicicletaRequestDTO);
        bicicleta.setCodigo(codigo);
        if (bicicleta.getStatus() == null) {
            bicicleta.setStatus(Bicicleta.Status.DISPONIVEL);
        }
        try {
            return bicicletaMapper.toResponse(bicicletaRepository.save(bicicleta));
        } catch (DataIntegrityViolationException ex) {
            throw new CodigoUniqueViolationException(String.format("O código '%s' já está cadastrado", codigo));
        }
    }

    @Transactional
    public BicicletaResponseDTO getById(Long id) {
        return bicicletaMapper.toResponse(buscarPorId(id));
    }

    @Transactional
    public BicicletaResponseDTO getByCodigo(String codigo) {
        return bicicletaMapper.toResponse(bicicletaRepository.findByCodigoIgnoreCase(normalizarCodigo(codigo)).orElseThrow(
                () -> new EntityNotFoundException(String.format("Bicicleta com código:%s não encontrada", codigo))
        ));
    }

    @Transactional
    public List<BicicletaResponseDTO> getAll() {
        return bicicletaMapper.toResponse(bicicletaRepository.findAll());
    }

    @Transactional
    public List<BicicletaResponseDTO> getByStatus(Bicicleta.Status status) {
        return bicicletaMapper.toResponse(bicicletaRepository.findAllByStatus(status));
    }

    @Transactional
    public BicicletaResponseDTO update(Long id, BicicletaRequestDTO bicicletaRequestDTO) {
        Bicicleta bicicleta = buscarPorId(id);
        String codigo = normalizarCodigo(bicicletaRequestDTO.codigo());

        if (bicicletaRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new CodigoUniqueViolationException(String.format("O código '%s' já está cadastrado", codigo));
        }

        Bicicleta.Status statusAtual = bicicleta.getStatus();
        if (statusAtual == Bicicleta.Status.ALUGADA
                && bicicletaRequestDTO.status() != null
                && bicicletaRequestDTO.status() != Bicicleta.Status.ALUGADA) {
            throw new RegraDeNegocioException("A bicicleta está em uma locação ativa. Registre a devolução antes de alterar o status.");
        }

        bicicletaMapper.updateBicicleta(bicicletaRequestDTO, bicicleta);
        bicicleta.setCodigo(codigo);
        if (bicicletaRequestDTO.status() == null) {
            bicicleta.setStatus(statusAtual);
        }

        return bicicletaMapper.toResponse(bicicletaRepository.save(bicicleta));
    }

    @Transactional
    public void delete(Long id) {
        Bicicleta bicicleta = buscarPorId(id);
        if (bicicleta.getStatus() == Bicicleta.Status.ALUGADA) {
            throw new RegraDeNegocioException("A bicicleta está em uma locação ativa e não pode ser removida.");
        }
        bicicletaRepository.delete(bicicleta);
    }

    @Transactional
    public Bicicleta buscarPorId(Long id) {
        return bicicletaRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException(String.format("Bicicleta com id:%s não encontrada", id))
        );
    }

    private String normalizarCodigo(String codigo) {
        return codigo == null ? null : codigo.trim().toUpperCase();
    }
}
