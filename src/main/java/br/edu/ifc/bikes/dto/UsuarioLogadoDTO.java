package br.edu.ifc.bikes.dto;

/**
 * Identificacao do usuario autenticado, usada pela interface para montar as telas
 * conforme o perfil (ADMIN ou CLIENTE) e para saber qual cliente esta logado.
 */
public record UsuarioLogadoDTO(
        Long id,
        String username,
        String role,
        Long clienteId
) { }
