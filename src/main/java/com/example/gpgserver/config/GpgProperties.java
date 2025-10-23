package com.example.gpgserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gpg")
public class GpgProperties {

    /**
     * Optional path to a public keyring file to be used as the default keyring.
     */
    private String publicKeyring;

    /**
     * Optional path to a secret keyring file to be used as the default keyring.
     */
    private String secretKeyring;

    /**
     * Optional trust model applied to every spawned GPG process. Valid values map to
     * {@link nl.base.crypto.gpg.GPG.TrustModel}.
     */
    private String trustModel;

    public String getPublicKeyring() {
        return publicKeyring;
    }

    public void setPublicKeyring(String publicKeyring) {
        this.publicKeyring = publicKeyring;
    }

    public String getSecretKeyring() {
        return secretKeyring;
    }

    public void setSecretKeyring(String secretKeyring) {
        this.secretKeyring = secretKeyring;
    }

    public String getTrustModel() {
        return trustModel;
    }

    public void setTrustModel(String trustModel) {
        this.trustModel = trustModel;
    }
}
