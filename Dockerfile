# Build em duas etapas: a imagem final nao carrega Maven, codigo-fonte nem cache
# de dependencias — so o JRE e o jar.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copiar o wrapper e o pom antes do codigo aproveita o cache de camadas do
# Docker: enquanto o pom nao mudar, o download de dependencias nao se repete a
# cada build. Inverter estas duas copias transformaria qualquer alteracao de
# codigo num download completo do repositorio Maven.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
# Os testes de integracao precisam de Docker e rodam no CI, nao aqui dentro:
# subir Testcontainers durante o build da imagem exigiria acesso ao socket do
# Docker de dentro do build, o que e tanto fragil quanto inseguro.
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario sem privilegios: se a aplicacao for comprometida, o atacante nao ganha
# root dentro do container.
RUN addgroup -S pedidos && adduser -S pedidos -G pedidos
USER pedidos

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# UTC explicito para que o comportamento nao dependa do fuso do host.
ENV TZ=UTC

# Sem isto, a JVM em container antigo ignorava o limite de memoria do cgroup e
# era morta pelo orquestrador. A porcentagem deixa a heap acompanhar o limite
# dado ao container, qualquer que seja ele.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
