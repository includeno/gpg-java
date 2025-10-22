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

    public byte[] decryptFile(String filePath, String passphrase, String publicKeyring, String secretKeyring)
            throws IOException {
        File cipherFile = new File(filePath);
        if (!cipherFile.exists() || !cipherFile.isFile()) {
            throw new FileNotFoundException("Encrypted file not found: " + filePath);
        }
        GPG gpg = resolveGpg(publicKeyring, secretKeyring);
        applyTrustModel(gpg, properties.getTrustModel());
        try (InputStream decrypted = gpg.decrypt(cipherFile, passphrase)) {
            return IOUtils.toByteArray(decrypted);
        }
    }

    public String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private GPG resolveGpg(String publicKeyring, String secretKeyring) throws IOException {
        if (StringUtils.isBlank(publicKeyring) && StringUtils.isBlank(secretKeyring)) {
            return defaultGpg;
        }
        if (StringUtils.isBlank(publicKeyring) || StringUtils.isBlank(secretKeyring)) {
            throw new IllegalArgumentException("Both public and secret keyring paths must be provided when overriding the default keyrings.");
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
