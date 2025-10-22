# Build stage
FROM maven:3.8.8-openjdk-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
COPY resources resources
RUN mvn -B -DskipTests package

# Runtime stage
FROM openjdk:8-jre-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends gnupg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
COPY scripts/entrypoint.sh /entrypoint.sh
COPY scripts/initialize-gpg.sh /usr/local/bin/initialize-gpg.sh
RUN chmod +x /entrypoint.sh /usr/local/bin/initialize-gpg.sh

ENV JAVA_OPTS=""
ENV LINUX_RMEM_MAX=""
ENV LINUX_WMEM_MAX=""
ENV GNUPGHOME=/app/.gnupg

EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
