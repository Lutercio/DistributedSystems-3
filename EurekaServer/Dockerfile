FROM maven:3.9.16-eclipse-temurin-26-alpine@sha256:17f5fa1caa0908f91a130d13a54137770e61b2b221220c008a35d86a21666959 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY APIGateway/pom.xml ./APIGateway/pom.xml
COPY ConfigServer/pom.xml ./ConfigServer/pom.xml
COPY EurekaServer/pom.xml ./EurekaServer/pom.xml
COPY QuestionService/pom.xml ./QuestionService/pom.xml
COPY RagService/pom.xml ./RagService/pom.xml
COPY QuestionNormalizerFunction/pom.xml ./QuestionNormalizerFunction/pom.xml
COPY RegulationMcpServer/pom.xml ./RegulationMcpServer/pom.xml
RUN mvn --batch-mode --no-transfer-progress -pl EurekaServer -am dependency:go-offline

COPY EurekaServer/src ./EurekaServer/src
RUN mvn --batch-mode --no-transfer-progress -pl EurekaServer -am -DskipTests package

FROM eclipse-temurin:26-jre-alpine@sha256:c4a22bec4f4368636abb9b6fe2b2350fd7fae1ec0d3bf43fcaae1be720c3bbd1

ARG RELEASE_VERSION=0.0.1
ARG BUILD_REVISION=unknown

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build /workspace/EurekaServer/target/eureka-*.jar app.jar

LABEL org.opencontainers.image.title="Distributed Systems Eureka Server" \
      org.opencontainers.image.version="${RELEASE_VERSION}" \
      org.opencontainers.image.revision="${BUILD_REVISION}"

USER spring:spring

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
