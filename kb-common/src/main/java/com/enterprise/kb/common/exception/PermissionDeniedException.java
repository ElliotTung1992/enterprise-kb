package com.enterprise.kb.common.exception;

import org.springframework.http.HttpStatus;

public class PermissionDeniedException extends KbException {

    public PermissionDeniedException() {
        super("Access denied", HttpStatus.FORBIDDEN);
    }

    public PermissionDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
