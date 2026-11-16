package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.UsuarioRequestDTO;
import br.edu.ifc.bikes.dto.UsuarioResponseDTO;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/usuarios/usuarios-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/usuarios/usuarios-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UsuarioIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String CLIENTE = "maria@gmail.com";
    private static final String SENHA = "123456";

    @Test
    void criarUsuario_DadosValidos_RetornaUsuarioCriado() {
        UsuarioResponseDTO responseBody = testClient
                .post()
                .uri("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioRequestDTO("fabio@gmail.com", "123456"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UsuarioResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.id()).isNotNull();
        assertThat(responseBody.username()).isEqualTo("fabio@gmail.com");
        assertThat(responseBody.role()).isEqualTo("CLIENTE");
    }

    @Test
    void criarUsuario_UsernameRepetido_RetornaErrorMessageStatus409() {
        ErrorMessage responseBody = testClient
                .post()
                .uri("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioRequestDTO("jose@gmail.com", "123456"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(409);
    }

    @Test
    void criarUsuario_UsernameInvalido_RetornaErrorMessageStatus422() {
        for (String usernameInvalido : new String[]{"", "jose@", "jose@gmail."}) {
            ErrorMessage responseBody = testClient
                    .post()
                    .uri("/api/v1/usuarios")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UsuarioRequestDTO(usernameInvalido, "123456"))
                    .exchange()
                    .expectStatus().isEqualTo(422)
                    .expectBody(ErrorMessage.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(responseBody).isNotNull();
            assertThat(responseBody.getStatus()).isEqualTo(422);
        }
    }

    @Test
    void criarUsuario_PasswordInvalido_RetornaErrorMessageStatus422() {
        ErrorMessage responseBody = testClient
                .post()
                .uri("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioRequestDTO("fabio@gmail.com", "12345"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(422);
    }

    @Test
    void buscarUsuario_IdExistente_RetornaUsuario() {
        String token = tokenFor(ADMIN, SENHA);

        UsuarioResponseDTO responseBody = testClient
                .get()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UsuarioResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.id()).isEqualTo(100);
        assertThat(responseBody.username()).isEqualTo("jose@gmail.com");
        assertThat(responseBody.role()).isEqualTo("ADMIN");
    }

    @Test
    void buscarUsuario_IdInexistente_RetornaErrorMessageStatus404() {
        String token = tokenFor(ADMIN, SENHA);

        ErrorMessage responseBody = testClient
                .get()
                .uri("/api/v1/usuarios/110")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getStatus()).isEqualTo(404);
    }

    @Test
    void buscarUsuario_ClienteConsultandoOutroUsuario_RetornaStatus403() {
        String token = tokenFor(CLIENTE, SENHA);

        testClient
                .get()
                .uri("/api/v1/usuarios/100")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void buscarUsuario_ClienteConsultandoProprioUsuario_RetornaUsuario() {
        String token = tokenFor(CLIENTE, SENHA);

        UsuarioResponseDTO responseBody = testClient
                .get()
                .uri("/api/v1/usuarios/101")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UsuarioResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.username()).isEqualTo("maria@gmail.com");
        assertThat(responseBody.role()).isEqualTo("CLIENTE");
    }

    @Test
    void listarUsuarios_ComAdmin_RetornaLista() {
        String token = tokenFor(ADMIN, SENHA);

        UsuarioResponseDTO[] responseBody = testClient
                .get()
                .uri("/api/v1/usuarios")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UsuarioResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody).hasSize(3);
    }

    @Test
    void listarUsuarios_ComCliente_RetornaStatus403() {
        String token = tokenFor(CLIENTE, SENHA);

        testClient
                .get()
                .uri("/api/v1/usuarios")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }
}
