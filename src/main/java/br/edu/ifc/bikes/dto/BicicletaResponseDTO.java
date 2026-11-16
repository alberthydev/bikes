package br.edu.ifc.bikes.dto;

import br.edu.ifc.bikes.entity.Bicicleta;

public record BicicletaResponseDTO(
        Long id,
        String codigo,
        String marca,
        String modelo,
        String cor,
        Integer aro,
        Bicicleta.Status status
) { }
