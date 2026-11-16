package br.edu.ifc.bikes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

public record ClienteRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,
        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF deve ser válido")
        String cpf,
        @Size(max = 15, message = "O telefone deve ter no máximo 15 caracteres")
        String telefone,
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres")
        String email
) { }
