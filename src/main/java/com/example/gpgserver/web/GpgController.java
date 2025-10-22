package com.example.gpgserver.web;

import com.example.gpgserver.service.GpgDecryptionService;
import com.example.gpgserver.web.dto.DecryptionResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/gpg", produces = MediaType.APPLICATION_JSON_VALUE)
public class GpgController {

    private final GpgDecryptionService gpgDecryptionService;

    public GpgController(GpgDecryptionService gpgDecryptionService) {
        this.gpgDecryptionService = gpgDecryptionService;
    }

    @GetMapping("/decrypt")
    public DecryptionResponse decrypt(@RequestParam("filePath") String filePath,
            @RequestParam("passphrase") String passphrase,
            @RequestParam(value = "publicKeyring", required = false) String publicKeyring,
            @RequestParam(value = "secretKeyring", required = false) String secretKeyring,
            @RequestParam(value = "encoding", defaultValue = "base64") String encoding) throws IOException {
        byte[] decrypted = gpgDecryptionService.decryptFile(filePath, passphrase, publicKeyring, secretKeyring);
        if ("plain".equalsIgnoreCase(encoding)) {
            return new DecryptionResponse(filePath, "plain", new String(decrypted, StandardCharsets.UTF_8));
        }
        if (!"base64".equalsIgnoreCase(encoding)) {
            throw new IllegalArgumentException("Unsupported encoding parameter: " + encoding);
        }
        return new DecryptionResponse(filePath, "base64", gpgDecryptionService.toBase64(decrypted));
    }
}
