package br.edu.ifc.bikes;

import br.edu.ifc.bikes.dto.UsuarioLoginDto;
import br.edu.ifc.bikes.jwt.JwtToken;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base para os testes de integracao: sobe o servidor em porta aleatoria,
 * cria o WebTestClient e oferece o login que devolve o token JWT.
 */
public abstract class IntegrationTestSupport {

    @LocalServerPort
    protected int port;

    protected WebTestClient testClient;

    @BeforeEach
    void setUpWebClient() {
        this.testClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    protected String tokenFor(String username, String password) {
        JwtToken body = testClient
                .post()
                .uri("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UsuarioLoginDto(username, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JwtToken.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getToken()).isNotBlank();
        return body.getToken();
    }
}
