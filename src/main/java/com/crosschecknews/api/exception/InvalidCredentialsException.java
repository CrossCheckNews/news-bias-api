package com.crosschecknews.api.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
