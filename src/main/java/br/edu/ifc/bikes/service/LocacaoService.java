package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.LocacaoRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoResponseDTO;
import br.edu.ifc.bikes.dto.mapper.LocacaoMapper;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.entity.Cliente;
import br.edu.ifc.bikes.entity.Locacao;
import br.edu.ifc.bikes.exception.EntityNotFoundException;
import br.edu.ifc.bikes.exception.RegraDeNegocioException;
import br.edu.ifc.bikes.repository.BicicletaRepository;
import br.edu.ifc.bikes.repository.ClienteRepository;
import br.edu.ifc.bikes.repository.LocacaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class LocacaoService {

    /** Tarifa definida para a demonstracao: R$ 5,00 por hora iniciada. */
    public static final BigDecimal VALOR_HORA = new BigDecimal("5.00");

    private final LocacaoRepository locacaoRepository;
    private final ClienteRepository clienteRepository;
    private final BicicletaRepository bicicletaRepository;
    private final LocacaoMapper locacaoMapper;

    /**
     * Cria a solicitacao de aluguel do cliente autenticado.
     * A bicicleta e bloqueada durante a transacao para impedir duas locacoes simultaneas.
     */
    @Transactional
    public LocacaoResponseDTO criar(LocacaoRequestDTO dto, Long usuarioId) {
        Cliente cliente = clienteRepository.findByUsuarioId(usuarioId).orElseThrow(
                () -> new RegraDeNegocioException("O usuário autenticado não possui cadastro de cliente.")
        );

        Bicicleta bicicleta = bicicletaRepository.buscarParaLocacao(dto.bicicletaId()).orElseThrow(
                () -> new EntityNotFoundException(String.format("Bicicleta com id:%s não encontrada", dto.bicicletaId()))
        );

        if (bicicleta.getStatus() != Bicicleta.Status.DISPONIVEL) {
            throw new RegraDeNegocioException(String.format(
                    "A bicicleta %s não está disponível (status: %s).", bicicleta.getCodigo(), bicicleta.getStatus()));
        }
        if (locacaoRepository.existsByBicicletaIdAndStatus(bicicleta.getId(), Locacao.Status.ATIVA)) {
            throw new RegraDeNegocioException(String.format(
                    "A bicicleta %s já participa de uma locação ativa.", bicicleta.getCodigo()));
        }

        LocalDateTime agora = LocalDateTime.now();
        if (!dto.previsaoDevolucao().isAfter(agora)) {
            throw new RegraDeNegocioException("A previsão de devolução deve ser posterior à data de retirada.");
        }

        Locacao locacao = new Locacao();
        locacao.setCliente(cliente);
        locacao.setBicicleta(bicicleta);
        locacao.setDataRetirada(agora);
        locacao.setPrevisaoDevolucao(dto.previsaoDevolucao());
        locacao.setStatus(Locacao.Status.ATIVA);
        locacao.setCartaoUltimosDigitos(ultimosDigitos(dto.numeroCartao()));

        bicicleta.setStatus(Bicicleta.Status.ALUGADA);
        bicicletaRepository.save(bicicleta);

        return locacaoMapper.toResponse(locacaoRepository.save(locacao));
    }

    /**
     * Registra a devolucao: grava a data efetiva, cobra as horas iniciadas e libera a bicicleta.
     * Se houve problema, a bicicleta vai para manutencao em vez de disponivel.
     */
    @Transactional
    public LocacaoResponseDTO devolver(Long id, boolean problema) {
        Locacao locacao = buscarPorId(id);
        if (locacao.getStatus() != Locacao.Status.ATIVA) {
            throw new RegraDeNegocioException("Esta locação já foi finalizada.");
        }

        LocalDateTime agora = LocalDateTime.now();
        long minutos = Math.max(0, Duration.between(locacao.getDataRetirada(), agora).toMinutes());
        long horas = Math.max(1, (long) Math.ceil(minutos / 60.0));

        locacao.setDevolucaoEfetiva(agora);
        locacao.setValor(VALOR_HORA.multiply(BigDecimal.valueOf(horas)));
        locacao.setStatus(Locacao.Status.FINALIZADA);

        Bicicleta bicicleta = locacao.getBicicleta();
        bicicleta.setStatus(problema ? Bicicleta.Status.MANUTENCAO : Bicicleta.Status.DISPONIVEL);
        bicicletaRepository.save(bicicleta);

        return locacaoMapper.toResponse(locacaoRepository.save(locacao));
    }

    @Transactional
    public LocacaoResponseDTO getById(Long id) {
        return locacaoMapper.toResponse(buscarPorId(id));
    }

    @Transactional
    public List<LocacaoResponseDTO> getAll() {
        return locacaoMapper.toResponse(locacaoRepository.findAllByOrderByDataRetiradaDesc());
    }

    @Transactional
    public List<LocacaoResponseDTO> getMinhas(Long usuarioId) {
        return locacaoMapper.toResponse(locacaoRepository.findByClienteUsuarioIdOrderByDataRetiradaDesc(usuarioId));
    }

    public boolean pertenceAoUsuario(Long locacaoId, Long usuarioId) {
        return locacaoRepository.existsByIdAndClienteUsuarioId(locacaoId, usuarioId);
    }

    @Transactional
    public Locacao buscarPorId(Long id) {
        return locacaoRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException(String.format("Locação com id:%s não encontrada", id))
        );
    }

    private String ultimosDigitos(String numeroCartao) {
        String digitos = numeroCartao.replaceAll("\\D", "");
        return digitos.substring(digitos.length() - 4);
    }
}
