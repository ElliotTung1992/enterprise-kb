package com.enterprise.kb.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends KbException {

    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
