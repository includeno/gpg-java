package nl.base.crypto.gpg.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import nl.base.crypto.gpg.GPG;

/**
 * Small command line helper that demonstrates how to decrypt the bundled PDF fixture
 * and optionally reproduces the "Stream closed" exception that is sometimes observed
 * when the ciphertext stream closes unexpectedly.
 */
public final class PdfDecryptDemo {

    private static final String PASSPHRASE = "JUnitPassphrase";
    private static final String ENCRYPTED_RESOURCE = "/junit/sample.pdf.gpg";
    private static final String PUBLIC_KEY_RESOURCE = "/junit/pubkey.asc";
    private static final String SECRET_KEY_RESOURCE = "/junit/seckey.asc";

    private PdfDecryptDemo() {
    }

    public static void main(String[] args) throws Exception {
        Mode mode = Mode.fromArgs(args);
        System.out.printf("Running PDF decrypt demo in %s mode%n", mode.name().toLowerCase());

        try (GpgHandle handle = buildTool()) {
            GPG gpg = handle.tool();
            switch (mode) {
                case NORMAL -> runNormalFlow(gpg);
                case BROKEN_STREAM -> runBrokenStreamFlow(gpg);
                default -> throw new IllegalStateException("Unhandled mode: " + mode);
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

    private static void runNormalFlow(GPG gpg) throws IOException {
        Path output = ensureOutputDirectory().resolve("sample-" + Instant.now().toEpochMilli() + ".pdf");
        try (InputStream cipher = resource(ENCRYPTED_RESOURCE); InputStream plain = gpg.decrypt(cipher, PASSPHRASE)) {
            Files.copy(plain, output, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.printf("Decrypted PDF written to %s%n", output);
    }

    private static void runBrokenStreamFlow(GPG gpg) throws IOException {
        try (InputStream cipher = resource(ENCRYPTED_RESOURCE);
             InputStream brokenCipher = new TruncatingInputStream(cipher);
             InputStream plain = gpg.decrypt(brokenCipher, PASSPHRASE)) {
            IOUtils.toByteArray(plain);
        } catch (IOException ex) {
            System.err.printf("Encountered expected exception: %s%n", ex);
            throw ex;
        }
    }

    private static Path ensureOutputDirectory() throws IOException {
        Path dir = Path.of("target", "pdf-output");
        Files.createDirectories(dir);
        return dir;
    }

    private static InputStream resource(String name) {
        InputStream stream = PdfDecryptDemo.class.getResourceAsStream(name);
        return Objects.requireNonNull(stream, () -> "Unable to locate resource " + name);
    }

    private enum Mode {
        NORMAL,
        BROKEN_STREAM;

        private static Mode fromArgs(String[] args) {
            if (args.length == 0) {
                return NORMAL;
            }
            String option = args[0].trim().toLowerCase();
            return switch (option) {
                case "normal" -> NORMAL;
                case "broken", "broken-stream", "broken_stream" -> BROKEN_STREAM;
                default -> throw new IllegalArgumentException("Unsupported mode: " + option);
            };
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
        private long remaining = 128;

        private TruncatingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                close();
                throw new IOException("Stream closed by test harness");
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                close();
                throw new IOException("Stream closed by test harness");
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            } else if (read == -1) {
                remaining = 0;
            }
            if (remaining <= 0) {
                close();
                throw new IOException("Stream closed by test harness");
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
