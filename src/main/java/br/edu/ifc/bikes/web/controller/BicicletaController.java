package br.edu.ifc.bikes.web.controller;

import br.edu.ifc.bikes.dto.BicicletaRequestDTO;
import br.edu.ifc.bikes.dto.BicicletaResponseDTO;
import br.edu.ifc.bikes.service.BicicletaService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bicicletas", description = "Contém todas as operações relativas aos recursos para cadastro, edição, leitura e remoção de uma bicicleta.")
@RestController
@RequestMapping("api/v1/bicicletas")
@RequiredArgsConstructor
public class BicicletaController {

    private final BicicletaService bicicletaService;

    @Operation(summary = "Criar uma nova bicicleta", description = "Recurso para criar uma nova bicicleta. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Recurso criado com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BicicletaResponseDTO.class))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Recurso não processado por dados de entrada inválidos",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BicicletaResponseDTO> create(@Valid @RequestBody BicicletaRequestDTO bicicleta) {
        BicicletaResponseDTO bicicletaCriada = bicicletaService.create(bicicleta);
        return ResponseEntity.status(HttpStatus.CREATED).body(bicicletaCriada);
    }

    @Operation(summary = "Recuperar uma bicicleta pelo id", description = "Recuperar uma bicicleta pelo id. Qualquer usuário autenticado. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recurso recuperado com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BicicletaResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Recurso não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    public ResponseEntity<BicicletaResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bicicletaService.getById(id));
    }

    @Operation(summary = "Listar todas as bicicletas", description = "Listar todas as bicicletas. Qualquer usuário autenticado. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista com todas as bicicletas cadastradas",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = BicicletaResponseDTO.class))))
            })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    public ResponseEntity<List<BicicletaResponseDTO>> getAll() {
        return ResponseEntity.ok(bicicletaService.getAll());
    }

    @Operation(summary = "Atualizar uma bicicleta", description = "Atualizar os dados de uma bicicleta. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recurso atualizado com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BicicletaResponseDTO.class))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "Recurso não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Recurso não processado por dados de entrada inválidos",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BicicletaResponseDTO> update(@PathVariable Long id,
                                                       @Valid @RequestBody BicicletaRequestDTO bicicleta) {
        return ResponseEntity.ok(bicicletaService.update(id, bicicleta));
    }

    @Operation(summary = "Remover uma bicicleta", description = "Remover uma bicicleta. Somente admin. Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Recurso removido com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Void.class))),
                    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para acessar este recurso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "Recurso não encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bicicletaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
