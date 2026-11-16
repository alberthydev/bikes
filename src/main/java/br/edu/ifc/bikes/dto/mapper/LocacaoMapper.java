package br.edu.ifc.bikes.dto.mapper;

import br.edu.ifc.bikes.dto.LocacaoResponseDTO;
import br.edu.ifc.bikes.entity.Locacao;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * A resposta da locacao expoe a duracao e os dados da bicicleta e do cliente.
 * Como esses valores sao derivados, o mapeamento e feito aqui, sem MapStruct.
 */
@Component
public class LocacaoMapper {

    public LocacaoResponseDTO toResponse(Locacao locacao) {
        Long duracao = locacao.getDevolucaoEfetiva() == null
                ? null
                : Math.max(0, Duration.between(locacao.getDataRetirada(), locacao.getDevolucaoEfetiva()).toMinutes());

        return new LocacaoResponseDTO(
                locacao.getId(),
                locacao.getCliente().getId(),
                locacao.getCliente().getNome(),
                locacao.getBicicleta().getId(),
                locacao.getBicicleta().getCodigo(),
                locacao.getDataRetirada(),
                locacao.getPrevisaoDevolucao(),
                locacao.getDevolucaoEfetiva(),
                duracao,
                locacao.getValor(),
                locacao.getStatus(),
                locacao.getCartaoUltimosDigitos()
        );
    }

    public List<LocacaoResponseDTO> toResponse(List<Locacao> locacoes) {
        return locacoes.stream().map(this::toResponse).toList();
    }
}
