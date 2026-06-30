FROM maven:3.9.16-eclipse-temurin-26-alpine AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:26-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build /workspace/target/gateway-*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
