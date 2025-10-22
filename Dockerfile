FROM maven:3.9.6-eclipse-temurin-17

RUN apt-get update \
    && apt-get install -y --no-install-recommends gnupg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY pom.xml ./
COPY resources ./resources
COPY src ./src
COPY docker ./docker

RUN mvn -B -DskipTests dependency:go-offline

ENTRYPOINT ["/bin/bash"]
