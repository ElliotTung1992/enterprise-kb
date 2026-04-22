package com.enterprise.kb.common.exception;

import org.springframework.http.HttpStatus;

public class KbException extends RuntimeException {

    private final HttpStatus status;

    public KbException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public KbException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
