package br.edu.ifc.bikes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@ToString
@Entity
@Table(name = "bicicletas")
@EntityListeners(AuditingEntityListener.class)
public class Bicicleta implements java.io.Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "marca", nullable = false, length = 50)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 50)
    private String modelo;

    @Column(name = "cor", nullable = false, length = 30)
    private String cor;

    @Column(name = "aro", nullable = false)
    private Integer aro;

    @CreatedDate
    private LocalDateTime dataCriacao;
    @LastModifiedDate
    private LocalDateTime dataModificacao;
    @CreatedBy
    private String criadoPor;
    @LastModifiedBy
    private String modificadoPor;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof Bicicleta bicicleta)) return false;
        return Objects.equals(id, bicicleta.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
