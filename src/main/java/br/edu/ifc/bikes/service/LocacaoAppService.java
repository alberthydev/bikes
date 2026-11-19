package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.LocacaoAppResponseDTO;
import br.edu.ifc.bikes.dto.LocacaoRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoResponseDTO;
import br.edu.ifc.bikes.entity.Cliente;
import br.edu.ifc.bikes.exception.CodigoAppInvalidoException;
import br.edu.ifc.bikes.service.CodigoAppService.CodigoDecodificado;
import br.edu.ifc.bikes.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Transforma o codigo do aplicativo em uma locacao real, reusando as regras
 * de tarifa, disponibilidade e bloqueio do {@link LocacaoService}.
 */
@RequiredArgsConstructor
@Service
public class LocacaoAppService {

    /** Cartao fixo do conceito; o sistema continua guardando so os 4 finais. */
    private static final String CARTAO_CONCEITO = "4111111111111111";

    private final CodigoAppService codigoAppService;
    private final LocacaoService locacaoService;
    private final ClienteRepository clienteRepository;

    public LocacaoAppResponseDTO importar(String codigo) {
        CodigoDecodificado dados = codigoAppService.validar(codigo);

        Cliente cliente = clienteRepository.findById(dados.clienteId()).orElseThrow(
                () -> new CodigoAppInvalidoException("O cliente do código não existe mais."));

        LocacaoRequestDTO solicitacao = new LocacaoRequestDTO(
                dados.bicicletaId(),
                dados.retirada().plusHours(dados.horas()),
                CARTAO_CONCEITO);

        LocacaoResponseDTO locacao = locacaoService.criar(solicitacao, cliente.getUsuario().getId());
        String confirmacao = codigoAppService.gerarConfirmacao(locacao.id(), dados.segredo());
        return new LocacaoAppResponseDTO(locacao, confirmacao);
    }
}
