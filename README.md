# gpg-java Spring Boot Service

This project wraps the original GPG command line integration in a Spring Boot 2.7
HTTP service while keeping the original `nl.base.crypto.gpg` implementation
unchanged. The service exposes a `GET /api/gpg/decrypt` endpoint that decrypts
PGP/GPG encrypted files on demand and returns their contents in either Base64 or
plain UTF-8 form.

## Features

- Reuses the existing Java GPG wrapper without modifying its source.
- Spring Boot 2.7 (Java 8 compatible) REST API for decrypting files via HTTP.
- Configurable keyring paths and trust model via `application.properties` or
  environment variables.
- Docker image that installs GNU Privacy Guard inside a generic Linux
  environment and exposes sysctl hooks for tuning Linux buffer sizes.
- `scripts/initialize-gpg.sh` helper to bootstrap GPG key material and optional
  encryption runs without generating binary artifacts by default.

## Project Layout

```
src/main/java
├── com/example/gpgserver
│   ├── config/GpgProperties.java
│   ├── service/GpgDecryptionService.java
│   └── web
│       ├── GpgController.java
│       ├── GpgExceptionHandler.java
│       └── dto
│           ├── DecryptionResponse.java
│           └── ErrorResponse.java
└── nl/base/crypto/gpg
    ├── GPG.java
    └── GPGKeyListParser.java
```

Additional utilities:

- `scripts/initialize-gpg.sh`
- `scripts/entrypoint.sh`
- `Dockerfile`
- `docker-compose.yml`

## Building and Running Locally

```bash
mvn spring-boot:run
```

By default the service listens on `http://localhost:8080`. Override the port by
setting `SERVER_PORT` or editing `src/main/resources/application.properties`.

### Decrypt Endpoint

```
GET /api/gpg/decrypt?filePath=/path/to/file.gpg&passphrase=secret&encoding=base64
```

Query parameters:

- `filePath` (required): Absolute path to the encrypted file inside the
  container or host.
- `passphrase` (required): Passphrase for the secret key.
- `publicKeyring`, `secretKeyring` (optional): Override keyring files for the
  request. Both must be provided together.
- `publicKeyData`, `secretKeyData` (optional): Inline key material supplied as
  Base64-encoded blobs or raw ASCII-armored blocks. Provide both parameters at
  the same time and omit the `publicKeyring`/`secretKeyring` overrides when
  using inline keys.
- `encoding` (optional): `base64` (default) or `plain`.

### Example Response

```json
{
  "filePath": "/workspace/resources/junit/gpgencrypted.gpg",
  "encoding": "base64",
  "payload": "LS0t..."
}
```

Errors are returned as JSON with HTTP status codes and timestamp metadata.

## Preparing GPG Keys

The repository keeps example keys under `resources/junit`. Import and (optionally)
create ciphertexts by running the helper script:

```bash
./scripts/initialize-gpg.sh \
  GPG_PUBLIC_KEY_PATH=resources/junit/pubkey.asc \
  GPG_SECRET_KEY_PATH=resources/junit/seckey.asc \
  GPG_ENCRYPT_SOURCE_PATH=resources/junit/sample.txt \
  GPG_ENCRYPT_RECIPIENT="Test User" \
  GPG_ENCRYPT_OUTPUT_PATH=/tmp/gpgencrypted.gpg
```

All file paths used by the script are configurable. Unless the source, recipient,
and output variables are explicitly supplied, the script only imports keys and
emits no binary artifacts, fulfilling the initialization-only requirement.

## Docker Usage

### Build the image

```bash
docker build -t gpg-svc .
```

> **Tip:** The runtime stage defaults to `openjdk:8-jre-slim`, but you can reuse
> an existing Java 8 base image such as `jdk8-fitnesse:latest` (which uses the
> `apk` package manager) by passing a build argument:
>
> ```bash
> docker build --build-arg RUNTIME_BASE=jdk8-fitnesse:latest -t gpg-svc .
> ```
>
> The Dockerfile automatically installs `gnupg`, `bash`, and `procps` using the
> detected package manager (`apk`, `apt-get`, or another compatible tool).

### Run with custom buffer sizes

```bash
docker run --rm -p 8080:8080 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -e LINUX_RMEM_MAX=12582912 \
  -e LINUX_WMEM_MAX=12582912 \
  -v gpg-data:/app/.gnupg \
  gpg-svc
```

The container entrypoint applies the buffer sizes using `sysctl` when the
process has the necessary privileges. For convenience, `docker-compose.yml`
exposes the same tuning knobs:

```bash
docker compose up --build
```

To switch the runtime base image when using Compose, supply the `RUNTIME_BASE`
environment variable at invocation time (for example,
`RUNTIME_BASE=jdk8-fitnesse:latest docker compose up --build`).

### Initialize Keys inside the Container

```bash
docker exec -it gpg-svc-1 initialize-gpg.sh \
  GPG_PUBLIC_KEY_PATH=/workspace/resources/junit/pubkey.asc \
  GPG_SECRET_KEY_PATH=/workspace/resources/junit/seckey.asc
```

### Providing Custom Key Files to the Container

If you have public/secret key material outside of the repository, mount it into
the container and point the initialization script at the mounted paths. The
paths you supply to `initialize-gpg.sh` must reference files that are visible
inside the container.

#### `docker run`

```bash
docker run --rm -p 8080:8080 \
  -v /absolute/path/to/keys:/workspace/external-keys:ro \
  gpg-svc

docker exec -it <container-id> initialize-gpg.sh \
  GPG_PUBLIC_KEY_PATH=/workspace/external-keys/your-public.asc \
  GPG_SECRET_KEY_PATH=/workspace/external-keys/your-secret.asc
```

#### `docker compose`

Add a bind mount to the `gpg-service` service (or set `HOST_KEYS_DIR` to an
existing directory before invoking Compose) and run the initializer with the
container-visible paths:

```yaml
services:
  gpg-service:
    volumes:
      - ${HOST_KEYS_DIR:-/absolute/path/to/keys}:/workspace/external-keys:ro
```

```bash
HOST_KEYS_DIR=/absolute/path/to/keys docker compose up --build -d
docker exec -it gpg-java-gpg-service-1 initialize-gpg.sh \
  GPG_PUBLIC_KEY_PATH=/workspace/external-keys/your-public.asc \
  GPG_SECRET_KEY_PATH=/workspace/external-keys/your-secret.asc
```

When you later invoke the HTTP decrypt endpoint, reuse the same container paths
for the `publicKeyring`/`secretKeyring` parameters or inline the key material
via `publicKeyData`/`secretKeyData`.

## Testing the Endpoint via Docker

After running the container and preparing the keys, trigger a decryption:

```bash
curl "http://localhost:8080/api/gpg/decrypt?filePath=/workspace/resources/junit/gpgencrypted.gpg&passphrase=secret"
```

The output payload contains the decrypted data in Base64 by default.

## Notes

- The underlying GPG Java wrapper remains untouched from the original
  repository.
- Java 8 is the required runtime and is configured through the Maven build as
  well as the Docker image.
- Customize keyring locations and trust model via environment variables or the
  `application.properties` file.
