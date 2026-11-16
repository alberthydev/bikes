package br.edu.ifc.bikes.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public record LocacaoRequestDTO(
        @NotNull(message = "A bicicleta é obrigatória")
        Long bicicletaId,
        @NotNull(message = "A previsão de devolução é obrigatória")
        @Future(message = "A previsão de devolução deve ser uma data futura")
        LocalDateTime previsaoDevolucao,
        @NotBlank(message = "O número do cartão é obrigatório")
        @Pattern(regexp = "^\\d{13,19}$", message = "O cartão deve ter entre 13 e 19 dígitos")
        String numeroCartao
) { }
