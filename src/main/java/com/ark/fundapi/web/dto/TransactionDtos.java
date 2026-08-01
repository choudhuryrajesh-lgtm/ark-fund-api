package com.ark.fundapi.web.dto;

import com.ark.fundapi.domain.Transaction;
import com.ark.fundapi.domain.TransactionDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    @Schema(name = "TransactionCreateRequest")
    public record CreateRequest(
            @NotNull UUID fundId,
            @NotNull UUID investorId,

            // A type code (e.g. "CONTRIBUTION"), not a fixed enum — valid codes
            // are discoverable via GET /api/v1/transaction-types. Resolved and
            // validated against transaction_types in the service layer, so an
            // unknown or retired code is rejected before a transaction is built.
            @NotBlank @Size(max = 40) String type,

            // Amount is always positive; the type decides whether it credits or
            // debits the fund. Digits caps it at the NUMERIC(19,2) column width
            // so oversized input fails validation rather than the database.
            @NotNull
            @DecimalMin(value = "0.01", message = "amount must be greater than zero")
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount,

            // Back-dating is legitimate (transactions are often recorded after
            // the fact); future-dating a ledger entry is not.
            @NotNull @PastOrPresent LocalDate transactionDate,

            @Size(max = 1000) String notes
    ) {
    }

    /**
     * Fund and investor are intentionally absent — re-pointing an existing
     * ledger entry at a different fund or investor silently rewrites two
     * parties' reported history. Correcting those means reversing the entry
     * and booking a new one, which is what an auditor expects to see.
     */
    @Schema(name = "TransactionUpdateRequest")
    public record UpdateRequest(
            @NotBlank @Size(max = 40) String type,
            @NotNull
            @DecimalMin(value = "0.01", message = "amount must be greater than zero")
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount,
            @NotNull @PastOrPresent LocalDate transactionDate,
            @Size(max = 1000) String notes
    ) {
    }

    @Schema(name = "TransactionResponse")
    public record Response(
            UUID id,
            UUID clientId,
            UUID fundId,
            String fundName,
            UUID investorId,
            String investorName,
            String type,
            TransactionDirection direction,
            BigDecimal amount,
            // The signed amount is returned alongside the raw amount so clients
            // never have to reimplement the credit/debit rule themselves.
            BigDecimal signedAmount,
            LocalDate transactionDate,
            String notes,
            Instant createdAt
    ) {
        public static Response from(Transaction transaction) {
            return new Response(
                    transaction.getId(),
                    transaction.getClient().getId(),
                    transaction.getFund().getId(),
                    transaction.getFund().getName(),
                    transaction.getInvestor().getId(),
                    transaction.getInvestor().getName(),
                    transaction.getType().getCode(),
                    transaction.getType().getDirection(),
                    transaction.getAmount(),
                    transaction.getSignedAmount(),
                    transaction.getTransactionDate(),
                    transaction.getNotes(),
                    transaction.getCreatedAt()
            );
        }
    }
}
