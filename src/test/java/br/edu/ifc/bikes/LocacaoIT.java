package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.dto.LocacaoRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoResponseDTO;
import br.edu.ifc.bikes.dto.UsuarioRequestDTO;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.entity.Locacao;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/teste-dados-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/teste-dados-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LocacaoIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String ANA = "maria@gmail.com";
    private static final String JOAO = "joao@gmail.com";
    private static final String SENHA = "123456";
    private static final long BIKE_DISPONIVEL = 400L;
    private static final long BIKE_MANUTENCAO = 401L;

    @Test
    void criarLocacao_BicicletaDisponivel_RetornaLocacaoAtiva() {
        LocacaoResponseDTO body = criarLocacao(ANA, BIKE_DISPONIVEL);

        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.clienteId()).isEqualTo(300L);
        assertThat(body.nomeCliente()).isEqualTo("Ana Teste");
        assertThat(body.codigoBicicleta()).isEqualTo("BK-400");
        assertThat(body.status()).isEqualTo(Locacao.Status.ATIVA);
        assertThat(body.dataRetirada()).isNotNull();
        assertThat(body.devolucaoEfetiva()).isNull();
        assertThat(body.valor()).isNull();
        assertThat(body.cartaoUltimosDigitos()).isEqualTo("1111");
    }

    @Test
    void criarLocacao_BicicletaFicaAlugada_RetornaStatusAlugada() {
        criarLocacao(ANA, BIKE_DISPONIVEL);

        BicicletaResponseDTO bicicleta = testClient
                .get()
                .uri("/api/v1/bicicletas/{id}", BIKE_DISPONIVEL)
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(bicicleta).isNotNull();
        assertThat(bicicleta.status()).isEqualTo(Bicicleta.Status.ALUGADA);
    }

    @Test
    void listarBicicletasDisponiveis_Cliente_RetornaSomenteDisponiveis() {
        BicicletaResponseDTO[] antes = testClient
                .get()
                .uri("/api/v1/locacoes/bicicletas-disponiveis")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        assertThat(antes).isNotNull();
        assertThat(antes).hasSize(1);
        assertThat(antes[0].codigo()).isEqualTo("BK-400");

        criarLocacao(ANA, BIKE_DISPONIVEL);

        BicicletaResponseDTO[] depois = testClient
                .get()
                .uri("/api/v1/locacoes/bicicletas-disponiveis")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        assertThat(depois).isNotNull().isEmpty();
    }

    @Test
    void criarLocacao_BicicletaJaAlugada_RetornaErrorMessageStatus409() {
        String token = tokenFor(ANA, SENHA);
        criarLocacao(ANA, BIKE_DISPONIVEL);

        ErrorMessage body = testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(BIKE_DISPONIVEL))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
    }

    @Test
    void criarLocacao_BicicletaAlugadaPorOutroCliente_RetornaErrorMessageStatus409() {
        criarLocacao(ANA, BIKE_DISPONIVEL);

        ErrorMessage body = testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(JOAO, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(BIKE_DISPONIVEL))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
    }

    @Test
    void criarLocacao_BicicletaEmManutencao_RetornaErrorMessageStatus409() {
        testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(BIKE_MANUTENCAO))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void criarLocacao_UsuarioSemCadastroDeCliente_RetornaErrorMessageStatus409() {
        String username = "sem.cliente." + System.nanoTime() + "@teste.com";
        testClient
                .post()
                .uri("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioRequestDTO(username, SENHA))
                .exchange()
                .expectStatus().isCreated();

        testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(username, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(BIKE_DISPONIVEL))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void criarLocacao_Admin_RetornaStatus403() {
        testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(BIKE_DISPONIVEL))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void criarLocacao_PrevisaoNoPassado_RetornaErrorMessageStatus422() {
        LocacaoRequestDTO solicitacao = new LocacaoRequestDTO(
                BIKE_DISPONIVEL, LocalDateTime.now().minusHours(1), "4111111111111111");

        testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(solicitacao)
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void devolverLocacao_SemProblema_CalculaValorELiberaBicicleta() {
        String token = tokenFor(ANA, SENHA);
        LocacaoResponseDTO locacao = criarLocacao(ANA, BIKE_DISPONIVEL);

        LocacaoResponseDTO body = testClient
                .post()
                .uri("/api/v1/locacoes/{id}/devolucao", locacao.id())
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LocacaoResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(Locacao.Status.FINALIZADA);
        assertThat(body.devolucaoEfetiva()).isNotNull();
        assertThat(body.duracaoMinutos()).isNotNull();
        assertThat(body.valor()).isNotNull();
        assertThat(body.valor()).isEqualByComparingTo(new BigDecimal("5.00"));

        BicicletaResponseDTO bicicleta = testClient
                .get()
                .uri("/api/v1/bicicletas/{id}", BIKE_DISPONIVEL)
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(bicicleta).isNotNull();
        assertThat(bicicleta.status()).isEqualTo(Bicicleta.Status.DISPONIVEL);
    }

    @Test
    void devolverLocacao_ComProblema_ColocaBicicletaEmManutencao() {
        String token = tokenFor(ANA, SENHA);
        LocacaoResponseDTO locacao = criarLocacao(ANA, BIKE_DISPONIVEL);

        testClient
                .post()
                .uri("/api/v1/locacoes/{id}/devolucao", locacao.id())
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("problema", true))
                .exchange()
                .expectStatus().isOk();

        BicicletaResponseDTO bicicleta = testClient
                .get()
                .uri("/api/v1/bicicletas/{id}", BIKE_DISPONIVEL)
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(bicicleta).isNotNull();
        assertThat(bicicleta.status()).isEqualTo(Bicicleta.Status.MANUTENCAO);
    }

    @Test
    void devolverLocacao_JaFinalizada_RetornaErrorMessageStatus409() {
        String token = tokenFor(ANA, SENHA);
        LocacaoResponseDTO locacao = criarLocacao(ANA, BIKE_DISPONIVEL);

        testClient.post().uri("/api/v1/locacoes/{id}/devolucao", locacao.id())
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();

        testClient.post().uri("/api/v1/locacoes/{id}/devolucao", locacao.id())
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void listarMinhasLocacoes_Cliente_RetornaSomenteAsProprias() {
        criarLocacao(ANA, BIKE_DISPONIVEL);

        LocacaoResponseDTO[] body = testClient
                .get()
                .uri("/api/v1/locacoes/minhas")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LocacaoResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body[0].nomeCliente()).isEqualTo("Ana Teste");
    }

    @Test
    void listarTodasLocacoes_Admin_RetornaLista() {
        criarLocacao(ANA, BIKE_DISPONIVEL);

        testClient
                .get()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void listarTodasLocacoes_Cliente_RetornaStatus403() {
        testClient
                .get()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void buscarLocacao_DeOutroCliente_RetornaStatus403() {
        LocacaoResponseDTO locacao = criarLocacao(ANA, BIKE_DISPONIVEL);

        testClient
                .get()
                .uri("/api/v1/locacoes/{id}", locacao.id())
                .headers(h -> h.setBearerAuth(tokenFor(JOAO, SENHA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void gerarRelatorio_Admin_RetornaPdf() {
        LocacaoResponseDTO locacao = criarLocacao(ANA, BIKE_DISPONIVEL);
        testClient.post().uri("/api/v1/locacoes/{id}/devolucao", locacao.id())
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isOk();

        byte[] pdf = testClient
                .get()
                .uri("/api/v1/locacoes/relatorio")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PDF)
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void gerarRelatorio_Cliente_RetornaStatus403() {
        testClient
                .get()
                .uri("/api/v1/locacoes/relatorio")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    private LocacaoResponseDTO criarLocacao(String username, long bicicletaId) {
        return testClient
                .post()
                .uri("/api/v1/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(username, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(novaSolicitacao(bicicletaId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(LocacaoResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    private LocacaoRequestDTO novaSolicitacao(long bicicletaId) {
        return new LocacaoRequestDTO(bicicletaId, LocalDateTime.now().plusHours(2), "4111111111111111");
    }
}
