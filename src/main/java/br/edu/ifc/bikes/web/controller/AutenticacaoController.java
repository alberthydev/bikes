package br.edu.ifc.bikes.web.controller;

import br.edu.ifc.bikes.dto.UsuarioLogadoDTO;
import br.edu.ifc.bikes.dto.UsuarioLoginDto;
import br.edu.ifc.bikes.jwt.JwtToken;
import br.edu.ifc.bikes.jwt.JwtUserDetails;
import br.edu.ifc.bikes.jwt.JwtUserDetailsService;
import br.edu.ifc.bikes.repository.ClienteRepository;
import br.edu.ifc.bikes.web.exception.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login e identificação do usuário autenticado.")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class AutenticacaoController {

    private final JwtUserDetailsService detailsService;
    private final AuthenticationManager authenticationManager;
    private final ClienteRepository clienteRepository;

    @Operation(summary = "Autenticar", description = "Recebe username e password e devolve o token JWT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Autenticado com sucesso",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtToken.class))),
                    @ApiResponse(responseCode = "400", description = "Credenciais inválidas",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            })
    @PostMapping("/auth")
    public ResponseEntity<?> autenticar(@RequestBody @Valid UsuarioLoginDto dto, HttpServletRequest request){
        try{
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(dto.username(), dto.password());
            authenticationManager.authenticate(authenticationToken);
            JwtToken token = detailsService.getTokenAuthenticated(dto.username());
            return ResponseEntity.ok(token);
        }catch (AuthenticationException ex){
            System.err.println("Credenciais inválidas!");
        }
        return ResponseEntity.badRequest().body(new ErrorMessage(request, HttpStatus.BAD_REQUEST, "Credenciais inválidas"));
    }

    @Operation(summary = "Usuário autenticado", description = "Devolve id, username, perfil e o id do cliente vinculado (quando existir). Requer token Bearer.",
            security = @SecurityRequirement(name = "security"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuário autenticado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioLogadoDTO.class)))
            })
    @GetMapping("/auth/me")
    public ResponseEntity<UsuarioLogadoDTO> eu(@AuthenticationPrincipal JwtUserDetails userDetails) {
        Long clienteId = clienteRepository.findByUsuarioId(userDetails.getId())
                .map(cliente -> cliente.getId())
                .orElse(null);
        return ResponseEntity.ok(new UsuarioLogadoDTO(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getRole(),
                clienteId));
    }
}
