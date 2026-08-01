package com.ark.fundapi.web.dto;

import com.ark.fundapi.domain.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Request and response payloads for clients.
 *
 * <p>DTOs are kept separate from JPA entities on purpose: the wire contract and
 * the persistence model change for different reasons and at different times.
 * Serialising entities directly also risks lazy-loading surprises and exposes
 * columns that were never meant to be public.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    @Schema(name = "ClientCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email
    ) {
    }

    @Schema(name = "ClientUpdateRequest")
    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email
    ) {
    }

    @Schema(name = "ClientResponse")
    public record Response(
            UUID id,
            String name,
            String email,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(Client client) {
            return new Response(
                    client.getId(),
                    client.getName(),
                    client.getEmail(),
                    client.getCreatedAt(),
                    client.getUpdatedAt()
            );
        }
    }
}
