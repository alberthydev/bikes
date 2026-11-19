package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.LocacaoAppRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoAppResponseDTO;
import br.edu.ifc.bikes.dto.PareamentoAppResponseDTO;
import br.edu.ifc.bikes.entity.Locacao;
import br.edu.ifc.bikes.service.CodigoAppService;
import br.edu.ifc.bikes.service.PareamentoService;
import br.edu.ifc.bikes.service.PareamentoService.Pareamento;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/teste-dados-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/teste-dados-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AppIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String ANA = "maria@gmail.com";
    private static final String SENHA = "123456";
    private static final long CLIENTE_ANA = 300L;
    private static final long BIKE_DISPONIVEL = 400L;
    private static final long BIKE_MANUTENCAO = 401L;

    @Autowired
    private PareamentoService pareamentoService;

    @Autowired
    private CodigoAppService codigoAppService;

    @Test
    void parear_Admin_RetornaSegredoECatalogo() {
        PareamentoAppResponseDTO body = testClient
                .post()
                .uri("/api/v1/app/pareamento")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new br.edu.ifc.bikes.dto.PareamentoAppRequestDTO(CLIENTE_ANA))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PareamentoAppResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isNotBlank();
        assertThat(body.segredo()).isNotBlank();
        assertThat(body.expiraEm()).isNotNull();
        assertThat(body.usuario().clienteId()).isEqualTo(CLIENTE_ANA);
        assertThat(body.usuario().nome()).isEqualTo("Ana Teste");
        assertThat(body.bicicletas()).hasSize(1);
        assertThat(body.bicicletas().get(0).codigo()).isEqualTo("BK-400");
    }

    @Test
    void parear_SemCliente_UsaPrimeiroClienteCadastrado() {
        PareamentoAppResponseDTO body = testClient
                .post()
                .uri("/api/v1/app/pareamento")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PareamentoAppResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.usuario().clienteId()).isEqualTo(CLIENTE_ANA);
    }

    @Test
    void importar_CodigoValido_CriaLocacaoComConfirmacao() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 2);

        LocacaoAppResponseDTO body = testClient
                .post()
                .uri("/api/v1/app/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LocacaoAppRequestDTO(codigo))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(LocacaoAppResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.locacao().id()).isNotNull();
        assertThat(body.locacao().clienteId()).isEqualTo(CLIENTE_ANA);
        assertThat(body.locacao().nomeCliente()).isEqualTo("Ana Teste");
        assertThat(body.locacao().codigoBicicleta()).isEqualTo("BK-400");
        assertThat(body.locacao().status()).isEqualTo(Locacao.Status.ATIVA);
        assertThat(body.locacao().cartaoUltimosDigitos()).isEqualTo("1111");
        assertThat(body.confirmacao()).isEqualTo(codigoAppService.gerarConfirmacao(body.locacao().id(), pareamento.segredo()));
        assertThat(body.confirmacao()).startsWith("L" + body.locacao().id() + "-");
    }

    @Test
    void importar_MesmoCodigoDuasVezes_RecusaRepeticao() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 1);

        importar(codigo).expectStatus().isCreated();

        ErrorMessage body = importar(codigo)
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
        assertThat(body.getMessage()).containsIgnoringCase("já foi utilizado");
    }

    @Test
    void importar_CodigoAdulterado_RetornaStatus422() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 1);
        String limpo = codigo.replace("-", "");
        int posicao = limpo.length() / 2;
        char original = limpo.charAt(posicao);
        String adulterado = limpo.substring(0, posicao)
                + (original == '0' ? '1' : '0')
                + limpo.substring(posicao + 1);

        ErrorMessage body = importar(adulterado)
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
    }

    @Test
    void importar_CodigoVencido_RetornaStatus422() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(
                pareamento, BIKE_DISPONIVEL, LocalDateTime.now().minusMinutes(20), 1);

        importar(codigo)
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.message").value(m -> assertThat(String.valueOf(m)).containsIgnoringCase("vencido"));
    }

    @Test
    void importar_BicicletaEmManutencao_RetornaStatus409() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(pareamento, BIKE_MANUTENCAO, LocalDateTime.now(), 1);

        importar(codigo).expectStatus().isEqualTo(409);
    }

    @Test
    void importar_BicicletaJaAlugada_RetornaStatus409() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String primeiro = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 1);
        String segundo = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 1);

        importar(primeiro).expectStatus().isCreated();
        importar(segundo).expectStatus().isEqualTo(409);
    }

    @Test
    void importar_CodigoVazio_RetornaStatus422() {
        importar("   ").expectStatus().isEqualTo(422);
    }

    @Test
    void parear_Cliente_RetornaStatus403() {
        testClient
                .post()
                .uri("/api/v1/app/pareamento")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void importar_Cliente_RetornaStatus403() {
        Pareamento pareamento = pareamentoService.criar(CLIENTE_ANA);
        String codigo = codigoAppService.gerar(pareamento, BIKE_DISPONIVEL, LocalDateTime.now(), 1);

        testClient
                .post()
                .uri("/api/v1/app/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ANA, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LocacaoAppRequestDTO(codigo))
                .exchange()
                .expectStatus().isForbidden();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec importar(String codigo) {
        return testClient
                .post()
                .uri("/api/v1/app/locacoes")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LocacaoAppRequestDTO(codigo))
                .exchange();
    }
}
