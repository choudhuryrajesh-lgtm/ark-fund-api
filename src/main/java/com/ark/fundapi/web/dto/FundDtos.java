package com.ark.fundapi.web.dto;

import com.ark.fundapi.domain.Fund;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FundDtos {

    private FundDtos() {
    }

    @Schema(name = "FundCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull LocalDate inceptionDate
    ) {
    }

    @Schema(name = "FundUpdateRequest")
    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull LocalDate inceptionDate
    ) {
    }

    @Schema(name = "FundResponse")
    public record Response(
            UUID id,
            UUID clientId,
            String name,
            String description,
            LocalDate inceptionDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(Fund fund) {
            return new Response(
                    fund.getId(),
                    fund.getClient().getId(),
                    fund.getName(),
                    fund.getDescription(),
                    fund.getInceptionDate(),
                    fund.getCreatedAt(),
                    fund.getUpdatedAt()
            );
        }
    }
}
