package br.edu.ifc.bikes.dto;

import br.edu.ifc.bikes.entity.Locacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocacaoResponseDTO(
        Long id,
        Long clienteId,
        String nomeCliente,
        Long bicicletaId,
        String codigoBicicleta,
        LocalDateTime dataRetirada,
        LocalDateTime previsaoDevolucao,
        LocalDateTime devolucaoEfetiva,
        Long duracaoMinutos,
        BigDecimal valor,
        Locacao.Status status,
        String cartaoUltimosDigitos
) { }
