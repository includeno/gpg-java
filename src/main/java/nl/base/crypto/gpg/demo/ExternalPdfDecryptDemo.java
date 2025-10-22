package nl.base.crypto.gpg.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.base.crypto.gpg.GPG;

/**
 * Command line helper that decrypts a user-provided ciphertext using the bundled
 * demo key material. The helper can also reproduce the "Stream closed"
 * exception by truncating the ciphertext stream before passing it to GPG.
 */
public final class ExternalPdfDecryptDemo {

    private static final String PASSPHRASE = "JUnitPassphrase";
    private static final String PUBLIC_KEY_RESOURCE = "/junit/pubkey.asc";
    private static final String SECRET_KEY_RESOURCE = "/junit/seckey.asc";
    private static final int DEFAULT_TRUNCATION_BYTES = 128;

    private ExternalPdfDecryptDemo() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            CliOptions.printUsage();
            return;
        }

        if (!Files.isReadable(options.ciphertext())) {
            throw new IOException("Ciphertext not readable: " + options.ciphertext());
        }

        try (GpgHandle handle = buildTool()) {
            GPG gpg = handle.tool();
            switch (options.mode()) {
                case NORMAL -> runNormalFlow(gpg, options);
                case BROKEN_STREAM -> runBrokenStreamFlow(gpg, options);
                default -> throw new IllegalStateException("Unhandled mode: " + options.mode());
            }
        }
    }

    private static GpgHandle buildTool() throws IOException {
        try (InputStream pub = resource(PUBLIC_KEY_RESOURCE); InputStream sec = resource(SECRET_KEY_RESOURCE)) {
            File pubRing = File.createTempFile("demo", ".pubring");
            File secRing = File.createTempFile("demo", ".secring");
            GPG gpg = new GPG(pubRing, secRing);
            gpg.importKey(pub);
            gpg.importKey(sec);
            return new GpgHandle(gpg, pubRing, secRing);
        }
    }

    private static void runNormalFlow(GPG gpg, CliOptions options) throws IOException {
        Path output = options.output().orElseGet(() -> defaultOutput(options.ciphertext()));
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream cipher = Files.newInputStream(options.ciphertext());
             InputStream plain = gpg.decrypt(cipher, PASSPHRASE)) {
            Files.copy(plain, output, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.printf("Decrypted PDF written to %s%n", output);
    }

    private static void runBrokenStreamFlow(GPG gpg, CliOptions options) throws IOException {
        long limit = options.truncateBytes();
        if (limit <= 0) {
            throw new IllegalArgumentException("Truncation limit must be positive, but was " + limit);
        }
        try (InputStream cipher = Files.newInputStream(options.ciphertext());
             InputStream brokenCipher = new TruncatingInputStream(cipher, limit);
             InputStream plain = gpg.decrypt(brokenCipher, PASSPHRASE)) {
            plain.readAllBytes();
        } catch (IOException ex) {
            System.err.printf("Encountered expected exception: %s%n", ex);
            throw ex;
        }
    }

    private static Path defaultOutput(Path ciphertext) {
        Path directory = Path.of("target", "pdf-output");
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to create output directory", ex);
        }
        String fileName = ciphertext.getFileName().toString();
        if (fileName.endsWith(".gpg")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        return directory.resolve(fileName + "-" + timestamp + ".pdf");
    }

    private static InputStream resource(String name) {
        InputStream stream = ExternalPdfDecryptDemo.class.getResourceAsStream(name);
        return Objects.requireNonNull(stream, () -> "Unable to locate resource " + name);
    }

    private enum Mode {
        NORMAL,
        BROKEN_STREAM;

        private static Mode fromToken(String token) {
            String option = token.trim().toLowerCase();
            return switch (option) {
                case "normal" -> NORMAL;
                case "broken", "broken-stream", "broken_stream" -> BROKEN_STREAM;
                default -> throw new IllegalArgumentException("Unsupported mode: " + token);
            };
        }
    }

    private record CliOptions(Mode mode, Path ciphertext, java.util.Optional<Path> output, long truncateBytes) {

        private static CliOptions parse(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Expected at least 2 arguments but got " + args.length);
            }
            Mode mode = Mode.fromToken(args[0]);
            Path ciphertext = Path.of(args[1]);
            List<String> extras = new ArrayList<>();
            long truncate = DEFAULT_TRUNCATION_BYTES;
            for (int i = 2; i < args.length; i++) {
                String token = args[i];
                if (token.startsWith("--truncate=")) {
                    truncate = parseTruncate(token.substring("--truncate=".length()));
                } else if (token.startsWith("--output=")) {
                    extras.add(token.substring("--output=".length()));
                } else {
                    extras.add(token);
                }
            }
            java.util.Optional<Path> output = extras.isEmpty() ? java.util.Optional.empty()
                                                               : java.util.Optional.of(Path.of(extras.get(0)));
            if (mode == Mode.NORMAL && output.isEmpty()) {
                output = java.util.Optional.of(defaultOutput(ciphertext));
            }
            return new CliOptions(mode, ciphertext, output, truncate);
        }

        private static long parseTruncate(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Unable to parse truncate value: " + value, ex);
            }
        }

        private static void printUsage() {
            System.err.println("Usage: ExternalPdfDecryptDemo <mode> <ciphertext> [outputPath|--output=PATH] [--truncate=N]");
            System.err.println("  mode: normal | broken");
            System.err.println("  ciphertext: path to the encrypted file to decrypt");
            System.err.println("  outputPath: optional destination for the decrypted PDF (defaults to target/pdf-output)");
            System.err.println("  --truncate: byte count to stream before simulating a closed stream in broken mode");
        }
    }

    private record GpgHandle(GPG tool, File publicRing, File secretRing) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            if (publicRing != null) {
                Files.deleteIfExists(publicRing.toPath());
            }
            if (secretRing != null) {
                Files.deleteIfExists(secretRing.toPath());
            }
        }
    }

    private static final class TruncatingInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private TruncatingInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            verifyRemaining();
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            if (remaining <= 0) {
                failClosed();
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            verifyRemaining();
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            } else if (read == -1) {
                remaining = 0;
            }
            if (remaining <= 0) {
                failClosed();
            }
            return read;
        }

        private void verifyRemaining() throws IOException {
            if (remaining <= 0) {
                failClosed();
            }
        }

        private void failClosed() throws IOException {
            close();
            throw new IOException("Stream closed by test harness");
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
