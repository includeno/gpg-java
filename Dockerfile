# Build stage
FROM maven:3.8.8-openjdk-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
COPY resources resources
RUN mvn -B -DskipTests package

# Runtime stage
ARG RUNTIME_BASE=openjdk:8-jre-slim
FROM ${RUNTIME_BASE}

RUN set -eux; \
    if command -v apk >/dev/null 2>&1; then \
        apk add --no-cache gnupg bash procps; \
    elif command -v apt-get >/dev/null 2>&1; then \
        apt-get update; \
        apt-get install -y --no-install-recommends gnupg procps bash; \
        rm -rf /var/lib/apt/lists/*; \
    else \
        echo "Unsupported package manager. Install gnupg, bash, and procps manually." >&2; \
        exit 1; \
    fi

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
