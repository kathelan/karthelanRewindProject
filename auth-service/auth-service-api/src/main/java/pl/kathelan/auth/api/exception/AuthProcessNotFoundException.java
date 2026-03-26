package pl.kathelan.auth.api.exception;

import java.util.UUID;

public class AuthProcessNotFoundException extends RuntimeException {
    public AuthProcessNotFoundException(UUID id) {
        super("Auth process not found: " + id);
    }
}