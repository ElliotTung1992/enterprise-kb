package com.enterprise.kb.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceExistException extends KbException {

    public ResourceExistException(String resource, Object key, Object value) {
        super(resource + " is already exist with " +  key + ": " + value, HttpStatus.CONFLICT);
    }

    public ResourceExistException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
