package br.edu.ifc.bikes.web.controller;

import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.dto.DevolucaoRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoRequestDTO;
import br.edu.ifc.bikes.dto.LocacaoResponseDTO;
import br.edu.ifc.bikes.jwt.JwtUserDetails;
import br.edu.ifc.bikes.service.JasperService;
import br.edu.ifc.bikes.service.LocacaoService;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Locações", description = "Solicitação, consulta e devolução das bicicletas alugadas.")
@RestController
@RequestMapping("api/v1/locacoes")
@RequiredArgsConstructor
public class LocacaoController {

    private final LocacaoService locacaoService;
    private final JasperService jasperService;

    @Operation(summary = "Solicitar uma locação", description = "O cliente autenticado solicita uma bicicleta disponível e informa os dados do cartão. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Locação criada com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocacaoResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Bicicleta não encontrada",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "409", description = "Bicicleta indisponível ou usuário sem cadastro de cliente",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Recurso não processado por dados de entrada inválidos",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<LocacaoResponseDTO> criar(@Valid @RequestBody LocacaoRequestDTO locacao,
                                                    @AuthenticationPrincipal JwtUserDetails userDetails) {
        LocacaoResponseDTO criada = locacaoService.criar(locacao, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @Operation(summary = "Listar todas as locações", description = "Listar todas as locações. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista com todas as locações",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = LocacaoResponseDTO.class))))
            })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LocacaoResponseDTO>> getAll() {
        return ResponseEntity.ok(locacaoService.getAll());
    }

    @Operation(summary = "Gerar o relatório de locações em PDF", description = "Gera o PDF com cliente, bicicleta, datas, duração, valor e status de cada locação. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "PDF gerado com sucesso",
                            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @GetMapping("/relatorio")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> relatorio() {
        byte[] pdf = jasperService.gerarRelatorioLocacoes();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=locacoes-" + System.currentTimeMillis() + ".pdf")
                .body(pdf);
    }

    @Operation(summary = "Listar as bicicletas disponíveis para locação", description = "Lista apenas as bicicletas com status DISPONIVEL, para o cliente escolher na solicitação. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de bicicletas disponíveis",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = BicicletaResponseDTO.class))))
            })
    @GetMapping("/bicicletas-disponiveis")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    public ResponseEntity<List<BicicletaResponseDTO>> bicicletasDisponiveis() {
        return ResponseEntity.ok(locacaoService.bicicletasDisponiveis());
    }

    @Operation(summary = "Listar as locações do cliente autenticado", description = "Listar as locações do cliente autenticado. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista com as locações do cliente",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = LocacaoResponseDTO.class))))
            })
    @GetMapping("/minhas")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<List<LocacaoResponseDTO>> getMinhas(@AuthenticationPrincipal JwtUserDetails userDetails) {
        return ResponseEntity.ok(locacaoService.getMinhas(userDetails.getId()));
    }

    @Operation(summary = "Recuperar uma locação pelo id", description = "O admin acessa qualquer locação; o cliente só acessa a própria. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recurso recuperado com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocacaoResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Recurso não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') OR (hasRole('CLIENTE') AND @locacaoService.pertenceAoUsuario(#id, authentication.principal.id))")
    public ResponseEntity<LocacaoResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(locacaoService.getById(id));
    }

    @Operation(summary = "Registrar a devolução", description = "Grava a data efetiva, calcula o valor em horas iniciadas e libera a bicicleta. O admin registra qualquer locação; o cliente só a própria. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Devolução registrada com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocacaoResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Recurso não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "409", description = "Locação já finalizada",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping("/{id}/devolucao")
    @PreAuthorize("hasRole('ADMIN') OR (hasRole('CLIENTE') AND @locacaoService.pertenceAoUsuario(#id, authentication.principal.id))")
    public ResponseEntity<LocacaoResponseDTO> devolver(@PathVariable Long id,
                                                       @RequestBody(required = false) DevolucaoRequestDTO devolucao) {
        boolean problema = devolucao != null && devolucao.problema();
        return ResponseEntity.ok(locacaoService.devolver(id, problema));
    }
}
