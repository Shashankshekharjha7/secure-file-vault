package com.vault.authservice.exception;

public class EmailConstraintViolationException extends RuntimeException {
    public EmailConstraintViolationException(String message, Throwable cause) {
        super(message, cause);


    }
}
