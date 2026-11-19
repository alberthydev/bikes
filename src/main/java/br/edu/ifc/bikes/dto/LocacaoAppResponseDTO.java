package br.edu.ifc.bikes.dto;

/**
 * Resultado da importacao: a locacao criada e um codigo curto que o aplicativo
 * valida localmente para confirmar que o painel registrou a solicitacao.
 */
public record LocacaoAppResponseDTO(LocacaoResponseDTO locacao, String confirmacao) {
}
