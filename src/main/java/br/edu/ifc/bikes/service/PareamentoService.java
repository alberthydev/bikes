package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.BicicletaAppDTO;
import br.edu.ifc.bikes.dto.LocacaoAppHistoricoDTO;
import br.edu.ifc.bikes.dto.PareamentoAppResponseDTO;
import br.edu.ifc.bikes.dto.UsuarioAppDTO;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.entity.Cliente;
import br.edu.ifc.bikes.exception.EntityNotFoundException;
import br.edu.ifc.bikes.repository.BicicletaRepository;
import br.edu.ifc.bikes.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pareamento entre o painel e o aplicativo.
 *
 * O painel gera um identificador e um segredo aleatorio, guardados apenas em
 * memoria por 30 minutos. O segredo vai no QR e assina os codigos que o
 * aplicativo produz, sem que o celular precise acessar a rede.
 */
@RequiredArgsConstructor
@Service
public class PareamentoService {

    /** Tempo de vida do pareamento exibido no QR. */
    public static final Duration VALIDADE = Duration.ofMinutes(30);

    private final ClienteRepository clienteRepository;
    private final BicicletaRepository bicicletaRepository;
    private final LocacaoService locacaoService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pareamento> ativos = new ConcurrentHashMap<>();

    /**
     * Cria um pareamento para o cliente informado. Sem cliente, usa o primeiro
     * cliente cadastrado, o que mantem a demonstracao simples.
     */
    public Pareamento criar(Long clienteId) {
        limparExpirados();
        Cliente cliente = resolverCliente(clienteId);

        Pareamento pareamento = new Pareamento(
                UUID.randomUUID().toString(),
                novoSegredo(),
                cliente.getId(),
                cliente.getNome(),
                cliente.getEmail() != null ? cliente.getEmail() : cliente.getUsuario().getUsername(),
                Instant.now().plus(VALIDADE),
                bicicletasDisponiveis(),
                locacaoService.getMinhas(cliente.getUsuario().getId()).stream()
                        .map(LocacaoAppHistoricoDTO::de)
                        .toList());

        ativos.put(pareamento.id(), pareamento);
        return pareamento;
    }

    /**
     * Pareamentos ainda validos. O servico de codigos testa o HMAC de cada um
     * ate encontrar o segredo que assinou o codigo digitado.
     */
    public List<Pareamento> ativos() {
        limparExpirados();
        return List.copyOf(ativos.values());
    }

    private Cliente resolverCliente(Long clienteId) {
        if (clienteId != null) {
            return clienteRepository.findById(clienteId).orElseThrow(
                    () -> new EntityNotFoundException(String.format("Cliente com id:%s não encontrado", clienteId)));
        }
        return clienteRepository.findAll().stream()
                .min(Comparator.comparing(Cliente::getId))
                .orElseThrow(() -> new EntityNotFoundException("Nenhum cliente cadastrado para parear o aplicativo"));
    }

    private List<BicicletaAppDTO> bicicletasDisponiveis() {
        return bicicletaRepository.findAllByStatus(Bicicleta.Status.DISPONIVEL).stream()
                .map(b -> new BicicletaAppDTO(b.getId(), b.getCodigo(), b.getModelo()))
                .toList();
    }

    private String novoSegredo() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void limparExpirados() {
        Instant agora = Instant.now();
        ativos.entrySet().removeIf(entrada -> entrada.getValue().expiraEm().isBefore(agora));
    }

    /** Dados de um pareamento ativo. */
    public record Pareamento(
            String id,
            String segredo,
            Long clienteId,
            String nomeCliente,
            String emailCliente,
            Instant expiraEm,
            List<BicicletaAppDTO> bicicletas,
            List<LocacaoAppHistoricoDTO> locacoes
    ) {
        public PareamentoAppResponseDTO toResponse() {
            return new PareamentoAppResponseDTO(
                    id,
                    segredo,
                    expiraEm,
                    new UsuarioAppDTO(nomeCliente, emailCliente, clienteId),
                    bicicletas,
                    locacoes);
        }
    }
}
