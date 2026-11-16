package br.edu.ifc.bikes.repository;

import br.edu.ifc.bikes.entity.Bicicleta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BicicletaRepository extends JpaRepository<Bicicleta, Long> {

    Optional<Bicicleta> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    List<Bicicleta> findAllByStatus(Bicicleta.Status status);

    /**
     * Bloqueia a linha da bicicleta durante a criacao da locacao.
     * Duas solicitacoes simultaneas para a mesma bicicleta passam a ser serializadas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bicicleta b where b.id = :id")
    Optional<Bicicleta> buscarParaLocacao(@Param("id") Long id);
}
