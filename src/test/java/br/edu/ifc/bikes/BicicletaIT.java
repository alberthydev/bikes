package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.BicicletaRequestDTO;
import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/teste-dados-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/teste-dados-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class BicicletaIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String CLIENTE = "maria@gmail.com";
    private static final String SENHA = "123456";

    @Test
    void criarBicicleta_DadosValidos_RetornaBicicletaDisponivel() {
        BicicletaResponseDTO body = testClient
                .post()
                .uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BicicletaRequestDTO("bk-900", "Caloi", "Elite", "Preta", 29, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BicicletaResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.codigo()).isEqualTo("BK-900");
        assertThat(body.status()).isEqualTo(Bicicleta.Status.DISPONIVEL);
    }

    @Test
    void criarBicicleta_CodigoRepetido_RetornaErrorMessageStatus409() {
        String token = tokenFor(ADMIN, SENHA);
        BicicletaRequestDTO bicicleta = new BicicletaRequestDTO("BK-900", "Caloi", "Elite", "Preta", 29, null);

        testClient.post().uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bicicleta)
                .exchange()
                .expectStatus().isCreated();

        ErrorMessage body = testClient
                .post()
                .uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BicicletaRequestDTO("bk-900", "Trek", "Marlin", "Azul", 29, null))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
    }

    @Test
    void criarBicicleta_SemCodigo_RetornaErrorMessageStatus422() {
        ErrorMessage body = testClient
                .post()
                .uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BicicletaRequestDTO("", "Caloi", "Elite", "Preta", 29, null))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
    }

    @Test
    void criarBicicleta_ClienteAutenticado_RetornaStatus403() {
        testClient
                .post()
                .uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(tokenFor(CLIENTE, SENHA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BicicletaRequestDTO("BK-901", "Caloi", "Elite", "Preta", 29, null))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void buscarBicicletas_ClienteAutenticado_RetornaStatus403() {
        String token = tokenFor(CLIENTE, SENHA);

        testClient.get().uri("/api/v1/bicicletas")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();

        testClient.get().uri("/api/v1/bicicletas/codigo/bk-400")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();

        testClient.get().uri("/api/v1/bicicletas/status/DISPONIVEL")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void buscarBicicleta_PorCodigo_RetornaBicicleta() {
        BicicletaResponseDTO body = testClient
                .get()
                .uri("/api/v1/bicicletas/codigo/bk-400")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(400L);
        assertThat(body.codigo()).isEqualTo("BK-400");
        assertThat(body.status()).isEqualTo(Bicicleta.Status.DISPONIVEL);
    }

    @Test
    void buscarBicicleta_CodigoInexistente_RetornaErrorMessageStatus404() {
        testClient
                .get()
                .uri("/api/v1/bicicletas/codigo/BK-NAO-EXISTE")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listarBicicletas_PorStatus_RetornaSomenteDoStatus() {
        BicicletaResponseDTO[] body = testClient
                .get()
                .uri("/api/v1/bicicletas/status/MANUTENCAO")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BicicletaResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body[0].codigo()).isEqualTo("BK-401");
    }
}
