package br.edu.ifc.bikes.dto;

import java.time.Instant;
import java.util.List;

/**
 * Dados que o painel coloca no QR de pareamento. O segredo assina os codigos
 * gerados pelo aplicativo e nunca deve ser exibido ao cliente final.
 */
public record PareamentoAppResponseDTO(
        String id,
        String segredo,
        Instant expiraEm,
        UsuarioAppDTO usuario,
        List<BicicletaAppDTO> bicicletas
) {
}
