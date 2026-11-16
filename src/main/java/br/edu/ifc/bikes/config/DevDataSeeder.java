package br.edu.ifc.bikes.config;

import br.edu.ifc.bikes.entity.Bicicleta;
import br.edu.ifc.bikes.entity.Cliente;
import br.edu.ifc.bikes.entity.Usuario;
import br.edu.ifc.bikes.repository.BicicletaRepository;
import br.edu.ifc.bikes.repository.ClienteRepository;
import br.edu.ifc.bikes.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Popula o banco H2 do perfil "dev" com dados prontos para a demonstracao.
 * Como o H2 esta em memoria, a carga acontece a cada inicializacao.
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final BicicletaRepository bicicletaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        Usuario admin = criarUsuario("admin@bikes.com", Usuario.Role.ROLE_ADMIN);
        Usuario clienteAna = criarUsuario("ana@bikes.com", Usuario.Role.ROLE_CLIENTE);
        Usuario clienteBruno = criarUsuario("bruno@bikes.com", Usuario.Role.ROLE_CLIENTE);

        criarCliente("Ana Souza", "52998224725", clienteAna);
        criarCliente("Bruno Lima", "11144477735", clienteBruno);

        criarBicicleta("Caloi", "Elite Carbon", "Preta", 29);
        criarBicicleta("Specialized", "Rockhopper", "Vermelha", 29);
        criarBicicleta("Trek", "Marlin 7", "Azul", 29);
        criarBicicleta("Sense", "Move Urban", "Cinza", 28);
        criarBicicleta("Oggi", "Hacker Sport", "Verde", 29);
        criarBicicleta("Cannondale", "Trail SE", "Laranja", 27);

        log.info("Dados de demonstracao criados. ADMIN: {} | CLIENTE: {} | senha: 123456",
                admin.getUsername(), clienteAna.getUsername());
    }

    private Usuario criarUsuario(String username, Usuario.Role role) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode("123456"));
        usuario.setRole(role);
        return usuarioRepository.save(usuario);
    }

    private void criarCliente(String nome, String cpf, Usuario usuario) {
        Cliente cliente = new Cliente();
        cliente.setNome(nome);
        cliente.setCpf(cpf);
        cliente.setUsuario(usuario);
        clienteRepository.save(cliente);
    }

    private void criarBicicleta(String marca, String modelo, String cor, Integer aro) {
        Bicicleta bicicleta = new Bicicleta();
        bicicleta.setMarca(marca);
        bicicleta.setModelo(modelo);
        bicicleta.setCor(cor);
        bicicleta.setAro(aro);
        bicicletaRepository.save(bicicleta);
    }
}
