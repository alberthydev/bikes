# Docker e CI/CD

Este documento cobre o mínimo para construir, rodar e publicar a Locabike.
O padrão do projeto é **MySQL**, o mesmo banco do tutorial e da entrega. A imagem da
aplicação é gerada em duas etapas (build com JDK + Maven Wrapper, execução só com JRE),
roda como usuário sem privilégio e é publicada no GHCR pelo GitHub Actions.

O MySQL **não fica dentro da imagem da aplicação**: ele roda como um serviço separado do
Compose. Um processo por contêiner é a prática recomendada e permite trocar, versionar e
fazer backup do banco sem reconstruir a aplicação.

## Rodar na sua máquina

Com Docker instalado, na raiz do projeto:

```bash
docker compose up --build
```

Esse comando sobe dois serviços:

| Serviço | Imagem | Papel |
| --- | --- | --- |
| `mysql` | `mysql:8.4` | Banco `bikes`, usuário `bikes`, volume `bikes-mysql` |
| `app` | construída do `Dockerfile` | Aplicação em `http://localhost:8080/` |

A aplicação espera o MySQL ficar saudável antes de iniciar. Na primeira subida o banco é
criado e o `DemoDataSeeder` insere as contas e bicicletas de demonstração:

- ADMIN: `admin@bikes.com` / `123456`
- CLIENTE: `ana@bikes.com` / `123456`
- CLIENTE: `bruno@bikes.com` / `123456`

O carregador só age quando a tabela de usuários está vazia, então reiniciar o compose não
duplica nem sobrescreve dados. Para desligar a carga (banco de produção), use
`APP_DEMO_DATA=false`.

Se a porta 8080 estiver ocupada (nesta máquina um `portproxy` a reserva):

```bash
BIKES_PORT=8081 docker compose up --build
```

No PowerShell: `$env:BIKES_PORT='8081'; docker compose up --build`.

Se a porta 3306 já tiver um MySQL local, mude a porta publicada do serviço:

```bash
MYSQL_PORT=3307 docker compose up --build
```

No PowerShell: `$env:MYSQL_PORT='3307'; docker compose up --build`.

Para parar sem apagar os dados:

```bash
docker compose down
```

Para apagar também o volume do MySQL e começar do zero:

```bash
docker compose down -v
```

## Modo H2 (sem MySQL)

Para uma demonstração rápida, sem banco externo, use o compose alternativo. Ele sobe só a
aplicação com o perfil `dev` (H2 em memória):

```bash
docker compose -f compose.h2.yaml up --build
```

O `compose.yaml` continua sendo o padrão do projeto.

## Configuração por variáveis de ambiente

| Variável | Padrão no compose | Para que serve |
| --- | --- | --- |
| `BIKES_PORT` | `8080` | Porta publicada da aplicação |
| `MYSQL_PORT` | `3306` | Porta publicada do MySQL |
| `MYSQL_PASSWORD` | `bikes` | Senha do usuário `bikes` e repassada à aplicação |
| `MYSQL_ROOT_PASSWORD` | `root` | Senha do `root` do MySQL (uso local) |
| `SPRING_PROFILES_ACTIVE` | `prod` | `prod` usa MySQL; `dev` usa H2 em memória |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/bikes?...` | URL JDBC usada pela aplicação |
| `SPRING_DATASOURCE_USERNAME` | `bikes` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `bikes` | Senha do banco |
| `APP_DEMO_DATA` | `true` | Cria contas/bicicletas de demonstração se o banco estiver vazio |
| `SERVER_PORT` | `8080` | Porta interna da aplicação |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` | Memória da JVM e encoding |
| `TZ` | `America/Sao_Paulo` | Fuso dos contêineres |

Para conectar com Workbench/DBeaver: host `localhost`, porta `3306`, banco `bikes`,
usuário `bikes`, senha `bikes`.

## Rodar a imagem sem o compose

A imagem do GHCR não inclui o MySQL. Suba um MySQL e uma rede, depois a aplicação:

```bash
docker network create bikes-net

docker run -d --name bikes-mysql --network bikes-net \
  -e MYSQL_DATABASE=bikes \
  -e MYSQL_USER=bikes \
  -e MYSQL_PASSWORD=bikes \
  -e MYSQL_ROOT_PASSWORD=root \
  -v bikes-mysql:/var/lib/mysql \
  mysql:8.4

# aguarde alguns segundos até o MySQL aceitar conexões

docker run -d --name bikes --network bikes-net -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://bikes-mysql:3306/bikes?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo' \
  -e SPRING_DATASOURCE_USERNAME=bikes \
  -e SPRING_DATASOURCE_PASSWORD=bikes \
  -e APP_DEMO_DATA=true \
  ghcr.io/alberthydev/bikes:latest
```

Também é possível apontar para um MySQL já existente, na sua máquina ou em outro servidor,
trocando apenas `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e
`SPRING_DATASOURCE_PASSWORD`. Nesse caso, crie o banco `bikes` (ou ajuste a URL) e garanta
que o usuário tenha permissão. As tabelas são criadas pelo `ddl-auto=update`.

## Pipeline

`.github/workflows/ci-cd.yml` roda em cada push na `main`, em tags `v*`, em pull requests e
manualmente:

1. **Testes**: `./mvnw verify` (47 testes de integração, H2).
2. **Imagem**: build multi-stage para `linux/amd64` e `linux/arm64`, push para o GHCR.
3. **Smoke test com MySQL**: sobe um MySQL 8.4 no job, executa a imagem publicada contra ele,
   confere a página inicial e faz login com a conta de demonstração. Isso valida o caminho
   padrão da entrega, não só o H2.

Não é preciso cadastrar segredo: o workflow usa o `GITHUB_TOKEN` para publicar em
`ghcr.io/alberthydev/bikes`. As tags geradas são `latest` (na `main`), `sha-<commit>`,
`pr-<número>` e a versão quando você cria uma tag `v1.2.3`.

Em pull requests a imagem não é publicada: ela é construída e testada localmente no runner.

## Baixar em qualquer máquina

Com o pacote público:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://SEU_HOST:3306/bikes?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo' \
  -e SPRING_DATASOURCE_USERNAME=bikes \
  -e SPRING_DATASOURCE_PASSWORD=bikes \
  ghcr.io/alberthydev/bikes:latest
```

Se o pacote estiver privado, autentique antes:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u SEU_USUARIO --password-stdin
```

Para tornar público: GitHub → seu perfil → **Packages** → `bikes` → **Package settings** →
**Change visibility** → **Public**. Isso precisa ser feito uma vez, depois do primeiro push.

## Publicar uma versão manualmente

```bash
docker build -t ghcr.io/alberthydev/bikes:latest .
docker push ghcr.io/alberthydev/bikes:latest
```

Para uma versão nomeada, crie uma tag Git e deixe o workflow publicar:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Notas

- O relatório Jasper é compilado durante o build; a imagem final inclui `fontconfig` e as
  fontes DejaVu para a geração dos PDFs.
- O build usa `$BUILDPLATFORM` e gera o jar uma única vez; a arquitetura alvo só afeta a
  camada final, então o build multi-arquitetura não passa pelo QEMU na etapa do Maven.
- Os dados do MySQL ficam no volume `bikes-mysql`. A aplicação em si não grava estado.
- Para inspecionar a saúde: `docker compose ps` ou
  `docker inspect --format '{{.State.Health.Status}}' bikes-app-1`.
