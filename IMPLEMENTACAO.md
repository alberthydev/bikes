# Implementação das demandas do projeto

Este documento descreve o que foi acrescentado ao projeto base do tutorial: locações,
devoluções, relatório de locações em PDF e uma interface web para demonstrar cada função.
A fonte normativa é o PDF **Demandas do projeto**; o `README.md` continua sendo o tutorial
de implementação do professor.

## Como executar

```powershell
# Demonstração: H2 em memória, dados recriados a cada inicialização
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

# Testes de integração (classes *IT)
.\mvnw.cmd verify

# Empacotar e rodar o jar
.\mvnw.cmd package
java -jar target/bikes-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

- Interface didática: <http://localhost:8080/>
- Swagger: <http://localhost:8080/docs-bikes.html>
- Console H2: <http://localhost:8080/h2-console> (`jdbc:h2:mem:bikes`, usuário `sa`)

> Se a porta 8080 estiver reservada por outro serviço da máquina (acontece em ambientes
> com WSL/Docker usando `portproxy`), rode com `--server.port=8081` e use
> <http://localhost:8081/>. Exemplo:
> `java -jar target/bikes-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=8081`.

### Docker

```bash
docker compose up --build
```

A imagem usa build multi-stage (JDK para compilar, JRE Alpine para executar), roda como
usuário sem privilégio e já sobe com o perfil `dev`. O workflow
`.github/workflows/ci-cd.yml` executa os testes e publica a imagem multi-arquitetura em
`ghcr.io/alberthydev/bikes`. Detalhes, variáveis de ambiente e uso com MySQL em
[DOCKER.md](DOCKER.md).

### Contas de demonstração

| Perfil  | Usuário           | Senha    |
|---------|-------------------|----------|
| ADMIN   | `admin@bikes.com` | `123456` |
| CLIENTE | `ana@bikes.com`   | `123456` |
| CLIENTE | `bruno@bikes.com` | `123456` |

O perfil `dev` cria também as bicicletas `BK-001` a `BK-006` (a `BK-006` inicia em manutenção).

## Modelo de dados

### Bicicleta

`codigo` (único, normalizado para maiúsculas), `marca`, `modelo`, `cor`, `aro` e `status`:

- `DISPONIVEL` — pode ser alugada;
- `ALUGADA` — existe uma locação ativa;
- `MANUTENCAO` — bloqueada para locação (inclusive após devolução com problema).

### Cliente

`nome`, `cpf` (único), `telefone` e `email` (opcionais) e o vínculo obrigatório com um `Usuario`.
Só é possível alugar se o usuário autenticado possuir um cadastro de cliente.

### Locação

`cliente`, `bicicleta`, `data_retirada`, `previsao_devolucao`, `devolucao_efetiva`,
`valor`, `status` (`ATIVA`/`FINALIZADA`) e `cartao_ultimos_digitos`.

- A previsão é separada da devolução efetiva: a primeira é informada na solicitação,
  a segunda é gravada no momento da devolução.
- Do cartão, apenas os 4 últimos dígitos são persistidos.

## Endpoints

### Autenticação

| Método | Caminho             | Permissão        | Descrição                                  |
|--------|---------------------|------------------|--------------------------------------------|
| POST   | `/api/v1/auth`      | pública          | Login; devolve o token JWT                 |
| GET    | `/api/v1/auth/me`   | autenticado      | Id, username, perfil e id do cliente (se houver) |

### Bicicletas

| Método | Caminho                            | Permissão        |
|--------|------------------------------------|------------------|
| POST   | `/api/v1/bicicletas`               | ADMIN            |
| GET    | `/api/v1/bicicletas`               | ADMIN            |
| GET    | `/api/v1/bicicletas/{id}`          | ADMIN            |
| GET    | `/api/v1/bicicletas/codigo/{codigo}` | ADMIN          |
| GET    | `/api/v1/bicicletas/status/{status}` | ADMIN          |
| PUT    | `/api/v1/bicicletas/{id}`          | ADMIN            |
| DELETE | `/api/v1/bicicletas/{id}`          | ADMIN            |

O recurso Bicicleta é restrito ao administrador, como pede o PDF. O cliente não consulta
bicicletas diretamente: para escolher uma bicicleta na locação ele usa
`GET /api/v1/locacoes/bicicletas-disponiveis`, que devolve apenas as `DISPONIVEL`.

### Clientes

| Método | Caminho                 | Permissão                     |
|--------|-------------------------|-------------------------------|
| POST   | `/api/v1/clientes`      | CLIENTE (cria o próprio)      |
| GET    | `/api/v1/clientes`      | ADMIN                         |
| GET    | `/api/v1/clientes/{id}` | ADMIN ou o próprio cliente    |

### Locações

| Método | Caminho                                     | Permissão                          |
|--------|---------------------------------------------|------------------------------------|
| POST   | `/api/v1/locacoes`                          | CLIENTE                            |
| GET    | `/api/v1/locacoes`                          | ADMIN                              |
| GET    | `/api/v1/locacoes/minhas`                   | CLIENTE                            |
| GET    | `/api/v1/locacoes/bicicletas-disponiveis`   | ADMIN/CLIENTE                      |
| GET    | `/api/v1/locacoes/{id}`                     | ADMIN ou o dono da locação         |
| POST   | `/api/v1/locacoes/{id}/devolucao`           | ADMIN ou o dono da locação         |
| GET    | `/api/v1/locacoes/relatorio`                | ADMIN (resposta `application/pdf`) |

#### Exemplo de solicitação

```json
POST /api/v1/locacoes
{
  "bicicletaId": 1,
  "previsaoDevolucao": "2026-11-16T20:30:00",
  "numeroCartao": "4111111111111111"
}
```

#### Exemplo de devolução

```json
POST /api/v1/locacoes/1/devolucao
{ "problema": false }
```

O corpo é opcional; sem ele, assume-se `problema = false`.

## Regras de negócio

- **Tarifa**: R$ 5,00 por hora iniciada (`LocacaoService.VALOR_HORA`).
- **Cálculo**: horas para cima, com mínimo de 1 hora; o valor só é gravado na devolução.
- **Disponibilidade**: só bicicletas `DISPONIVEL` podem ser alugadas. A bicicleta é bloqueada
  com lock pessimista durante a criação da locação, impedindo duas locações simultâneas.
- **Devolução**: grava `devolucao_efetiva`, finaliza a locação e libera a bicicleta.
  Se `problema = true`, a bicicleta vai para `MANUTENCAO`.
- **Locação já finalizada**: nova devolução responde **409**.
- **Bicicleta em locação ativa**: não pode ser excluída nem ter o status alterado (**409**).
- **Usuário sem cadastro de cliente**: não pode alugar (**409**).
- **Código de bicicleta duplicado / CPF duplicado**: **409**.
- **Dados inválidos** (previsão no passado, cartão fora de 13–19 dígitos, CPF inválido): **422**.

## Relatório de locações (JasperReports)

- Fonte versionado: `src/main/resources/reports/locacoes.jrxml` (formato do JasperReports 7).
- O build (`process-classes`) compila o `.jrxml` para `target/classes/reports/locacoes.jasper`
  usando a classe `br.edu.ifc.bikes.tools.CompilarRelatorios`. Assim o relatório também
  funciona no jar empacotado, onde o compilador Java do Jasper não enxerga as dependências aninhadas.
- Em execução pela IDE (sem o `.jasper` no classpath), o `JasperService` compila o `.jrxml`
  em runtime — em nenhum cenário é necessário o Jaspersoft Studio.
- Os dados vêm de um `JRBeanCollectionDataSource` com `LocacaoRelatorioDTO` (já formatado em
  textos), o que mantém o relatório independente do banco e de locale.
- O PDF é servido em `GET /api/v1/locacoes/relatorio`, restrito a `ADMIN`.

## Interface didática

A tela em `/` (arquivos em `src/main/resources/static/app`) é HTML, CSS e JavaScript puros,
servida pelo próprio Spring:

- **Login** com as contas de demonstração e exibição do perfil retornado por `/auth/me`.
- **Bicicletas**: cadastro (admin), busca por código, filtro por status, troca de status e exclusão.
- **Locações e devoluções**: solicitação pelo cliente, lista das próprias locações, lista geral
  para o admin, devolução com marcação de problema e download do relatório em PDF.
- **Clientes**: lista para o admin e criação do próprio cadastro pelo cliente que ainda não tem um.
- **Chamadas à API**: histórico com método, caminho, status, tempo e corpos enviados/recebidos,
  para evidenciar o funcionamento de cada endpoint durante a apresentação.

## Aderência às demandas do PDF

Checklist dos requisitos da descrição do projeto:

| Requisito | Onde está atendido |
|-----------|--------------------|
| Timezone do país | `SpringTimezoneConfig` (`America/Sao_Paulo`) + `spring.jackson.time-zone` |
| Locale do país | `spring.web.locale=pt_BR` e `spring.jackson.locale=pt_BR` |
| Auditoria (criação/modificação e usuário) | `@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy` em todas as entidades + `SpringJpaAuditingConfig` |
| Base de dados de desenvolvimento | Perfil `dev` com H2 em memória; MySQL como padrão |
| Usuário: e-mail único, senha de 6, perfis, cadastro público, busca por id, troca de senha, listagem admin | `UsuarioController`/`UsuarioService` + `UsuarioIT`/`UsuarioSenhaIT` |
| Cliente: nome, CPF único, vínculo 1:1 com usuário, cadastro só pelo cliente, consulta própria/admin | `ClienteController`/`ClienteService` + `ClienteIT` |
| Bicicleta: código único, status livre/ocupada/manutenção, busca por código, ações só admin | `BicicletaController` (ADMIN) + `BicicletaIT` |
| Locação: solicitação, verificação de disponibilidade, cartão, retirada, previsão e devolução efetiva, sem duas locações simultâneas | `LocacaoService` (`buscarParaLocacao` com lock pessimista) + `LocacaoIT` |
| Devolução: data efetiva, tempo/valor, bicicleta livre, manutenção em caso de problema | `LocacaoService.devolver` + `LocacaoIT` |
| Relatório: cliente, bicicleta, retirada/devolução, duração e status | `reports/locacoes.jrxml` + `JasperService` |

Observações sobre o modelo de dados:

- O diagrama mostra `data_devolucao`; o requisito 6 pede **previsão** e **devolução efetiva**,
  por isso a locação tem `previsao_devolucao` e `devolucao_efetiva`.
- A coluna de vínculo do cliente chama-se `id_usuario` (o diagrama usa `usuario_id`); o
  mapeamento JPA é o mesmo e o nome foi mantido para não quebrar bancos já criados.

## Testes

`.\mvnw.cmd verify` executa 47 testes de integração:

| Classe          | Testes | Cobertura resumida                                              |
|-----------------|--------|-----------------------------------------------------------------|
| `BicicletaIT`   | 8      | cadastro, código único, validação, permissões, busca, status     |
| `ClienteIT`     | 7      | cadastro com telefone/e-mail, CPF único, validação, permissões   |
| `LocacaoIT`     | 17     | criação, conflitos, devolução, cálculo do valor, relatório, PDF  |
| `UsuarioIT`     | 10     | recursos de usuário existentes                                   |
| `UsuarioSenhaIT`| 5      | alteração de senha existente                                     |

A massa de dados dos novos testes está em
`src/test/resources/sql/teste-dados-insert.sql` / `teste-dados-delete.sql`.
