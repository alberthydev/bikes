# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa 1: build. Compila o projeto e empacota o jar com o Maven Wrapper.
# A cache de dependencias fica em /root/.m2 e nao vai para a imagem final.
# ---------------------------------------------------------------------------
# O build roda na arquitetura da máquina que constrói (BUILDPLATFORM), nao na
# arquitetura alvo. O jar e portavel, entao o build multi-arquitetura nao passa
# pelo QEMU na etapa mais cara.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package

# ---------------------------------------------------------------------------
# Etapa 2: execucao. Somente o JRE e o jar, rodando como usuario sem privilegio.
# fontconfig/DejaVu atendem ao JasperReports na geracao dos PDFs.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/bikes-*.jar /app/app.jar

# Padrao do projeto: MySQL (perfil prod). O compose sobe o servidor MySQL e
# injeta SPRING_DATASOURCE_URL/USERNAME/PASSWORD e APP_DEMO_DATA=true.
# Para rodar sem banco, use -e SPRING_PROFILES_ACTIVE=dev (H2 em memoria).
ENV TZ=America/Sao_Paulo \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT:-8080}/" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
