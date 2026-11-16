package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.BicicletaRequestDTO;
import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.dto.mapper.BicicletaMapper;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.exception.EntityNotFoundException;
import br.edu.ifc.bikes.repository.BicicletaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BicicletaService {

    private final BicicletaRepository bicicletaRepository;
    private final BicicletaMapper bicicletaMapper;

    @Transactional
    public BicicletaResponseDTO create(BicicletaRequestDTO bicicletaRequestDTO) {
        Bicicleta bicicleta = bicicletaMapper.toBicicleta(bicicletaRequestDTO);
        return bicicletaMapper.toResponse(bicicletaRepository.save(bicicleta));
    }

    @Transactional
    public BicicletaResponseDTO getById(Long id) {
        return bicicletaMapper.toResponse(buscarPorId(id));
    }

    @Transactional
    public List<BicicletaResponseDTO> getAll() {
        return bicicletaMapper.toResponse(bicicletaRepository.findAll());
    }

    @Transactional
    public BicicletaResponseDTO update(Long id, BicicletaRequestDTO bicicletaRequestDTO) {
        Bicicleta bicicleta = buscarPorId(id);
        bicicletaMapper.updateBicicleta(bicicletaRequestDTO, bicicleta);
        return bicicletaMapper.toResponse(bicicletaRepository.save(bicicleta));
    }

    @Transactional
    public void delete(Long id) {
        bicicletaRepository.delete(buscarPorId(id));
    }

    private Bicicleta buscarPorId(Long id) {
        return bicicletaRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException(String.format("Bicicleta com id:%s nao encontrada", id))
        );
    }
}
