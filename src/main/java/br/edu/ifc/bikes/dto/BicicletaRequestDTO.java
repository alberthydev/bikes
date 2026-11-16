package br.edu.ifc.bikes.dto;

import br.edu.ifc.bikes.entity.Bicicleta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BicicletaRequestDTO(
        @NotBlank(message = "O código é obrigatório")
        @Size(max = 20, message = "O código deve ter no máximo 20 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "O código deve conter apenas letras, números e hífen")
        String codigo,
        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 50, message = "A marca deve ter no máximo 50 caracteres")
        String marca,
        @NotBlank(message = "O modelo é obrigatório")
        @Size(max = 50, message = "O modelo deve ter no máximo 50 caracteres")
        String modelo,
        @NotBlank(message = "A cor é obrigatória")
        @Size(max = 30, message = "A cor deve ter no máximo 30 caracteres")
        String cor,
        @NotNull(message = "O aro é obrigatório")
        @Min(value = 12, message = "O aro deve ser no mínimo 12")
        @Max(value = 29, message = "O aro deve ser no máximo 29")
        Integer aro,
        Bicicleta.Status status
) { }
