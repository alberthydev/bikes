package br.edu.ifc.bikes.repository;

import br.edu.ifc.bikes.entity.Locacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocacaoRepository extends JpaRepository<Locacao, Long> {

    List<Locacao> findAllByOrderByDataRetiradaDesc();

    List<Locacao> findByClienteUsuarioIdOrderByDataRetiradaDesc(Long usuarioId);

    boolean existsByBicicletaIdAndStatus(Long bicicletaId, Locacao.Status status);

    boolean existsByIdAndClienteUsuarioId(Long id, Long usuarioId);

    Optional<Locacao> findByIdAndClienteUsuarioId(Long id, Long usuarioId);
}
