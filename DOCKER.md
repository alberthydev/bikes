# Docker e CI/CD

Este documento cobre o mínimo para construir, rodar e publicar a imagem da Locabike.
A imagem é gerada em duas etapas (build com JDK + Maven Wrapper, execução só com JRE),
roda como usuário sem privilégio e é publicada no GHCR pelo GitHub Actions.

## Rodar na sua máquina

Com Docker instalado, na raiz do projeto:

```bash
docker compose up --build
```

Abra <http://localhost:8080/>. A imagem já sobe com o perfil `dev` (H2 em memória, dados de
demonstração recriados a cada start).

Se a porta 8080 estiver ocupada (nesta máquina um `portproxy` a reserva):

```bash
BIKES_PORT=8081 docker compose up --build
```

No PowerShell: `$env:BIKES_PORT='8081'; docker compose up --build`.

Sem Compose:

```bash
docker build -t bikes:local .
docker run --rm -p 8080:8080 bikes:local
```

## Configuração por variáveis de ambiente

| Variável | Padrão na imagem | Para que serve |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` usa H2 em memória; remova/ajuste para usar MySQL |
| `SERVER_PORT` | `8080` | Porta interna da aplicação |
| `SPRING_DATASOURCE_URL` | — | URL JDBC do MySQL, ex.: `jdbc:mysql://host:3306/bikes?...` |
| `SPRING_DATASOURCE_USERNAME` | — | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | — | Senha do banco |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` | Memória da JVM e encoding |
| `TZ` | `America/Sao_Paulo` | Fuso do contêiner |

Exemplo com MySQL:

```bash
docker run -d --name bikes -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://host.docker.internal:3306/bikes?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo' \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=root \
  ghcr.io/alberthydev/bikes:latest
```

## Pipeline

`.github/workflows/ci-cd.yml` roda em cada push na `main`, em tags `v*`, em pull requests e
manualmente:

1. **Testes**: `./mvnw verify` (47 testes de integração, H2).
2. **Imagem**: build multi-stage para `linux/amd64` e `linux/arm64`, push para o GHCR, e um
   smoke test que sobe a imagem publicada e confere a página inicial.

Não é preciso cadastrar segredo: o workflow usa o `GITHUB_TOKEN` para publicar em
`ghcr.io/alberthydev/bikes`. As tags geradas são `latest` (na `main`), `sha-<commit>`,
`pr-<número>` e a versão quando você cria uma tag `v1.2.3`.

Em pull requests a imagem não é publicada: ela é construída e testada localmente no runner.

## Baixar em qualquer máquina

Com o pacote público:

```bash
docker run --rm -p 8080:8080 ghcr.io/alberthydev/bikes:latest
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
- A imagem não grava dados em disco: no perfil `dev` o H2 é em memória, e no MySQL os dados
  ficam no servidor de banco.
- Para inspecionar a saúde do contêiner: `docker inspect --format '{{.State.Health.Status}}' bikes`.
