-- Senha de todos os usuarios de teste: 123456 (hash BCrypt)
insert into USUARIOS (id, username, password, role)
    values (100, 'jose@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_ADMIN');
insert into USUARIOS (id, username, password, role)
    values (101, 'maria@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_CLIENTE');
insert into USUARIOS (id, username, password, role)
    values (102, 'joao@gmail.com', '$2a$10$bpGdWDx1Cbuf.9kHcENBdutLKOaJTDwibV4ulvz3vssZKD8.BawYe', 'ROLE_CLIENTE');
