package br.edu.ifc.bikes.dto.mapper;

import br.edu.ifc.bikes.dto.UsuarioRequestDTO;
import br.edu.ifc.bikes.dto.UsuarioResponseDTO;
import br.edu.ifc.bikes.entity.Usuario;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {
    Usuario toUsuario(UsuarioRequestDTO usuarioRequestDTO);
    UsuarioResponseDTO toResponse(Usuario usuario);
    List<UsuarioResponseDTO> toResponse(List<Usuario> usuarios);

    /**
     * Expõe o papel sem o prefixo exigido pelo Spring Security: ROLE_ADMIN -> ADMIN.
     * O prefixo continua no banco; apenas o contrato da API fica mais simples.
     */
    default String toRole(Usuario.Role role) {
        if (role == null) {
            return null;
        }
        String nome = role.name();
        return nome.startsWith("ROLE_") ? nome.substring("ROLE_".length()) : nome;
    }
}