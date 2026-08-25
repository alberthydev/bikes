package br.edu.ifc.bikes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

// Lombok
@Getter
@Setter
@ToString

@Entity
@Table(name = "usuario")
public class Usuario implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    // A constante definida no enum passa a ser uma string no banco
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Role role = Role.CLIENTE;

    private LocalDateTime dtCriacao;
    private LocalDateTime dtModificacao;
    private String criadoPor;
    private String modificadoPor;

    public enum Role {
        ADMIN, CLIENTE;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) return false;
        if(obj == this) return true;
        Usuario usuario = (Usuario) obj;
        return Object.equals(id, usuario.id);
    }
}
