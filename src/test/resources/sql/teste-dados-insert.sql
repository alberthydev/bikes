-- Massa de dados comum aos testes de bicicleta, cliente e locacao.
-- Senha de todos os usuarios: 123456 (hash BCrypt)
insert into usuarios (id, username, password, role)
    values (100, 'jose@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_ADMIN');
insert into usuarios (id, username, password, role)
    values (101, 'maria@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_CLIENTE');
insert into usuarios (id, username, password, role)
    values (102, 'joao@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_CLIENTE');

insert into clientes (id, nome, cpf, telefone, email, id_usuario)
    values (300, 'Ana Teste', '52998224725', '(49) 99911-2233', 'ana@teste.com', 101);
insert into clientes (id, nome, cpf, telefone, email, id_usuario)
    values (301, 'Joao Teste', '11144477735', '(49) 98844-5566', 'joao@teste.com', 102);

insert into bicicletas (id, codigo, marca, modelo, cor, aro, status)
    values (400, 'BK-400', 'Caloi', 'Elite Carbon', 'Preta', 29, 'DISPONIVEL');
insert into bicicletas (id, codigo, marca, modelo, cor, aro, status)
    values (401, 'BK-401', 'Trek', 'Marlin 7', 'Azul', 29, 'MANUTENCAO');
