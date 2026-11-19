package br.edu.ifc.bikes.web.controller;

import br.edu.ifc.bikes.dto.LocacaoAppRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoAppResponseDTO;
import br.edu.ifc.bikes.dto.PareamentoAppRequestDTO;
import br.edu.ifc.bikes.dto.PareamentoAppResponseDTO;
import br.edu.ifc.bikes.service.LocacaoAppService;
import br.edu.ifc.bikes.service.PareamentoService;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pontos de contato do aplicativo conceitual. O celular não acessa a API:
 * o painel gera o QR de pareamento e depois importa o código digitado.
 */
@Tag(name = "Aplicativo", description = "Pareamento por QR e importação do código gerado pelo aplicativo.")
@RestController
@RequestMapping("api/v1/app")
@RequiredArgsConstructor
public class AppController {

    private final PareamentoService pareamentoService;
    private final LocacaoAppService locacaoAppService;

    @Operation(summary = "Gerar o pareamento do aplicativo",
            description = "Cria um segredo de curta duração e a lista de bicicletas disponíveis para o QR. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Pareamento criado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PareamentoAppResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping("/pareamento")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PareamentoAppResponseDTO> parear(
            @RequestBody(required = false) PareamentoAppRequestDTO pedido) {
        Long clienteId = pedido == null ? null : pedido.clienteId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pareamentoService.criar(clienteId).toResponse());
    }

    @Operation(summary = "Importar uma locação gerada no aplicativo",
            description = "Valida o código assinado, cria a locação com as regras normais e devolve a confirmação para o aplicativo. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Locação criada",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocacaoAppResponseDTO.class))),
                    @ApiResponse(responseCode = "409", description = "Bicicleta indisponível ou cliente sem cadastro",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Código inválido, vencido ou já utilizado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping("/locacoes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocacaoAppResponseDTO> importar(
            @Valid @RequestBody LocacaoAppRequestDTO pedido) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locacaoAppService.importar(pedido.codigo()));
    }
}
