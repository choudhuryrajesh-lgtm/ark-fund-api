package com.ark.fundapi.web.dto;

import com.ark.fundapi.domain.Investor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class InvestorDtos {

    private InvestorDtos() {
    }

    @Schema(name = "InvestorCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email
    ) {
    }

    @Schema(name = "InvestorUpdateRequest")
    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email
    ) {
    }

    @Schema(name = "InvestorResponse")
    public record Response(
            UUID id,
            UUID clientId,
            String name,
            String email,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(Investor investor) {
            return new Response(
                    investor.getId(),
                    investor.getClient().getId(),
                    investor.getName(),
                    investor.getEmail(),
                    investor.getCreatedAt(),
                    investor.getUpdatedAt()
            );
        }
    }
}
