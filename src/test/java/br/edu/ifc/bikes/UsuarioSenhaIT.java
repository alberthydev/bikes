package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.UsuarioSenhaDTO;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/usuarios/usuarios-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/usuarios/usuarios-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UsuarioSenhaIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String CLIENTE = "maria@gmail.com";
    private static final String SENHA = "123456";

    @Test
    void alterarSenha_DadosValidos_RetornaNoContent() {
        String token = tokenFor(ADMIN, SENHA);

        testClient
                .patch()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioSenhaDTO("123456", "654321", "654321"))
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    void alterarSenha_SenhaAtualInvalida_RetornaBadRequest() {
        String token = tokenFor(ADMIN, SENHA);

        ErrorMessage responseBody = testClient
                .patch()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioSenhaDTO("000000", "654321", "654321"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(400);
    }

    @Test
    void alterarSenha_NovaSenhaEConfirmacaoDivergentes_RetornaBadRequest() {
        String token = tokenFor(ADMIN, SENHA);

        ErrorMessage responseBody = testClient
                .patch()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioSenhaDTO("123456", "654321", "123456"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(400);
    }

    @Test
    void alterarSenha_CamposInvalidos_RetornaUnprocessableContent() {
        String token = tokenFor(ADMIN, SENHA);

        ErrorMessage responseBody = testClient
                .patch()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioSenhaDTO("", "12345", "1234567"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(422);
    }

    @Test
    void alterarSenha_ClienteAlterandoOutroUsuario_RetornaStatus403() {
        String token = tokenFor(CLIENTE, SENHA);

        testClient
                .patch()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioSenhaDTO("123456", "654321", "654321"))
                .exchange()
                .expectStatus().isForbidden();
    }
}
