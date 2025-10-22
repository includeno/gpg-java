package com.example.gpgserver.web.dto;

import java.time.Instant;

public class ErrorResponse {

    private final Instant timestamp = Instant.now();
    private final String error;
    private final String message;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}
