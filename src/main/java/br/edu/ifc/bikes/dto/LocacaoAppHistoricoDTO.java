package br.edu.ifc.bikes.dto;

import br.edu.ifc.bikes.entity.Locacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

public record LocacaoAppHistoricoDTO(
        Long id,
        Long bicicletaId,
        String codigoBicicleta,
        Instant retirada,
        Instant previsaoDevolucao,
        Instant devolucaoEfetiva,
        BigDecimal valor,
        Locacao.Status status
) {
    public static LocacaoAppHistoricoDTO de(LocacaoResponseDTO locacao) {
        ZoneId fuso = ZoneId.systemDefault();
        return new LocacaoAppHistoricoDTO(
                locacao.id(),
                locacao.bicicletaId(),
                locacao.codigoBicicleta(),
                locacao.dataRetirada().atZone(fuso).toInstant(),
                locacao.previsaoDevolucao().atZone(fuso).toInstant(),
                locacao.devolucaoEfetiva() == null ? null : locacao.devolucaoEfetiva().atZone(fuso).toInstant(),
                locacao.valor(),
                locacao.status());
    }
}
