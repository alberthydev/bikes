package br.edu.ifc.bikes.dto;

import jakarta.validation.constraints.NotBlank;

public record LocacaoAppRequestDTO(
        @NotBlank(message = "O código do aplicativo é obrigatório")
        String codigo
) {
}
