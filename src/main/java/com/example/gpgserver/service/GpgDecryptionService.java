package com.example.gpgserver.service;

import com.example.gpgserver.config.GpgProperties;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import nl.base.crypto.gpg.GPG;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class GpgDecryptionService {

    private final GpgProperties properties;
    private final GPG defaultGpg;

    public GpgDecryptionService(GpgProperties properties) throws IOException {
        this.properties = properties;
        this.defaultGpg = createDefaultGpg();
        applyTrustModel(defaultGpg, properties.getTrustModel());
    }

    public byte[] decryptFile(String filePath, String passphrase, String publicKeyring, String secretKeyring,
            String publicKeyData, String secretKeyData) throws IOException {
        File cipherFile = new File(filePath);
        if (!cipherFile.exists() || !cipherFile.isFile()) {
            throw new FileNotFoundException("Encrypted file not found: " + filePath);
        }
        GPG gpg = resolveGpg(publicKeyring, secretKeyring, publicKeyData, secretKeyData);
        applyTrustModel(gpg, properties.getTrustModel());
        try (InputStream decrypted = gpg.decrypt(cipherFile, passphrase)) {
            return IOUtils.toByteArray(decrypted);
        }
    }

    public String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private GPG resolveGpg(String publicKeyring, String secretKeyring, String publicKeyData, String secretKeyData)
            throws IOException {
        boolean hasData = StringUtils.isNotBlank(publicKeyData) || StringUtils.isNotBlank(secretKeyData);
        if (hasData) {
            if (StringUtils.isBlank(publicKeyData) || StringUtils.isBlank(secretKeyData)) {
                throw new IllegalArgumentException(
                        "Both publicKeyData and secretKeyData must be provided when supplying inline key material.");
            }
            if (StringUtils.isNotBlank(publicKeyring) || StringUtils.isNotBlank(secretKeyring)) {
                throw new IllegalArgumentException(
                        "Do not mix keyring paths with inline key data. Provide either paths or data for both keys.");
            }
            File pub = materializeKey("public", publicKeyData);
            File sec = materializeKey("secret", secretKeyData);
            return new GPG(pub, sec);
        }
        if (StringUtils.isBlank(publicKeyring) && StringUtils.isBlank(secretKeyring)) {
            return defaultGpg;
        }
        if (StringUtils.isBlank(publicKeyring) || StringUtils.isBlank(secretKeyring)) {
            throw new IllegalArgumentException(
                    "Both public and secret keyring paths must be provided when overriding the default keyrings.");
        }
        File pub = new File(publicKeyring);
        if (!pub.exists() || !pub.isFile()) {
            throw new FileNotFoundException("Public keyring not found: " + publicKeyring);
        }
        File sec = new File(secretKeyring);
        if (!sec.exists() || !sec.isFile()) {
            throw new FileNotFoundException("Secret keyring not found: " + secretKeyring);
        }
        return new GPG(pub, sec);
    }

    private File materializeKey(String prefix, String keyData) throws IOException {
        byte[] decoded = decodeKeyMaterial(keyData);
        File temp = File.createTempFile("gpg-" + prefix + "-", ".asc");
        temp.deleteOnExit();
        java.nio.file.Files.write(temp.toPath(), decoded);
        return temp;
    }

    private byte[] decodeKeyMaterial(String keyData) {
        String trimmed = keyData == null ? "" : keyData.trim();
        if (trimmed.startsWith("-----BEGIN")) {
            return trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Key data must be ASCII-armored text or Base64 encoded string including the armored headers.", ex);
        }
    }

    private GPG createDefaultGpg() throws IOException {
        boolean hasPublic = StringUtils.isNotBlank(properties.getPublicKeyring());
        boolean hasSecret = StringUtils.isNotBlank(properties.getSecretKeyring());
        if (hasPublic && hasSecret) {
            File pub = new File(properties.getPublicKeyring());
            File sec = new File(properties.getSecretKeyring());
            return new GPG(pub, sec);
        }
        if (hasPublic ^ hasSecret) {
            throw new IllegalStateException(
                    "Both gpg.public-keyring and gpg.secret-keyring must be provided to configure default keyrings.");
        }
        return new GPG();
    }

    private void applyTrustModel(GPG gpg, String trustModel) {
        if (StringUtils.isBlank(trustModel)) {
            return;
        }
        try {
            GPG.TrustModel model = GPG.TrustModel.valueOf(trustModel.trim().toUpperCase());
            gpg.setTrustModel(model);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported trust model value: " + trustModel, ex);
        }
    }
}
