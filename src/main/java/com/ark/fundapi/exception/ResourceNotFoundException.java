package com.ark.fundapi.exception;

import java.util.UUID;

/** Thrown when a resource does not exist, or exists but belongs to a different client. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super("%s %s was not found".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
