package com.example.gpgserver.web.dto;

public class DecryptionResponse {

    private final String filePath;
    private final String encoding;
    private final String payload;

    public DecryptionResponse(String filePath, String encoding, String payload) {
        this.filePath = filePath;
        this.encoding = encoding;
        this.payload = payload;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getPayload() {
        return payload;
    }
}
