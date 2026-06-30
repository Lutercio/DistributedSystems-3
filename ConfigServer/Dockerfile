FROM maven:3.9.16-eclipse-temurin-26-alpine AS build

WORKDIR /workspace

COPY ConfigServer/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY ConfigServer/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:26-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build /workspace/target/config-*.jar app.jar
COPY config-repository /config-repository

RUN apk add --no-cache git \
    && cd /config-repository \
    && git init -b main \
    && git config user.name "Config Server" \
    && git config user.email "config-server@localhost" \
    && git add . \
    && git commit -m "Seed centralized configuration" \
    && apk del git \
    && mkdir -p /home/spring/.config/jgit \
    && chown -R spring:spring /home/spring /config-repository

ENV XDG_CONFIG_HOME=/home/spring/.config

USER spring:spring

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
