package com.ark.fundapi.web.dto;

import com.ark.fundapi.domain.TransactionDirection;
import com.ark.fundapi.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

public final class TransactionTypeDtos {

    private TransactionTypeDtos() {
    }

    @Schema(name = "TransactionTypeResponse")
    public record Response(
            String code,
            TransactionDirection direction,
            String description
    ) {
        public static Response from(TransactionType type) {
            return new Response(type.getCode(), type.getDirection(), type.getDescription());
        }
    }
}