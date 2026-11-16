package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.ClienteRequestDTO;
import br.edu.ifc.bikes.dto.ClienteResponseDTO;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/sql/teste-dados-insert.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/teste-dados-delete.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClienteIT extends IntegrationTestSupport {

    private static final String ADMIN = "jose@gmail.com";
    private static final String CLIENTE = "maria@gmail.com";
    private static final String SENHA = "123456";

    @Test
    void criarCliente_DadosValidos_RetornaClienteComTelefoneEEmail() {
        String token = tokenDeNovoUsuario();

        ClienteResponseDTO body = testClient
                .post()
                .uri("/api/v1/clientes")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ClienteRequestDTO("Novo Cliente", "39053344705",
                        "(49) 90000-0000", "novo@teste.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ClienteResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.nome()).isEqualTo("Novo Cliente");
        assertThat(body.telefone()).isEqualTo("(49) 90000-0000");
        assertThat(body.email()).isEqualTo("novo@teste.com");
    }

    @Test
    void criarCliente_CpfJaCadastrado_RetornaErrorMessageStatus409() {
        String token = tokenDeNovoUsuario();

        testClient
                .post()
                .uri("/api/v1/clientes")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ClienteRequestDTO("Outro Nome", "52998224725", null, null))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void criarCliente_EmailInvalido_RetornaErrorMessageStatus422() {
        String token = tokenDeNovoUsuario();

        ErrorMessage body = testClient
                .post()
                .uri("/api/v1/clientes")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ClienteRequestDTO("Outro Nome", "39053344705", null, "email-invalido"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
    }

    @Test
    void buscarCliente_ProprioCadastro_RetornaCliente() {
        ClienteResponseDTO body = testClient
                .get()
                .uri("/api/v1/clientes/300")
                .headers(h -> h.setBearerAuth(tokenFor(CLIENTE, SENHA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ClienteResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.nome()).isEqualTo("Ana Teste");
    }

    @Test
    void buscarCliente_CadastroDeOutroCliente_RetornaStatus403() {
        testClient
                .get()
                .uri("/api/v1/clientes/300")
                .headers(h -> h.setBearerAuth(tokenFor("joao@gmail.com", SENHA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void buscarCliente_Admin_RetornaCliente() {
        testClient
                .get()
                .uri("/api/v1/clientes/300")
                .headers(h -> h.setBearerAuth(tokenFor(ADMIN, SENHA)))
                .exchange()
                .expectStatus().isOk();
    }

    private String tokenDeNovoUsuario() {
        String username = "cliente.novo." + System.nanoTime() + "@teste.com";
        testClient
                .post()
                .uri("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new br.edu.ifc.bikes.dto.UsuarioRequestDTO(username, SENHA))
                .exchange()
                .expectStatus().isCreated();

        return tokenFor(username, SENHA);
    }
}
