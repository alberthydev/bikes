package br.edu.ifc.bikes.dto;

/**
 * Pedido de pareamento do aplicativo. O cliente e opcional; sem ele, o primeiro
 * cliente cadastrado e usado na demonstracao.
 */
public record PareamentoAppRequestDTO(Long clienteId) {
}
