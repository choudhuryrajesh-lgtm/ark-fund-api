package com.ark.fundapi.web;

import com.ark.fundapi.service.TransactionService;
import com.ark.fundapi.web.dto.PageResponse;
import com.ark.fundapi.web.dto.TransactionDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/transactions")
@Tag(name = "Transactions", description = "Investor transactions against funds")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Record a transaction for an investor against a fund")
    public ResponseEntity<TransactionDtos.Response> create(
            @PathVariable UUID clientId,
            @Valid @RequestBody TransactionDtos.CreateRequest request) {
        TransactionDtos.Response created = transactionService.create(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List transactions, optionally filtered by fund or investor")
    public PageResponse<TransactionDtos.Response> list(
            @PathVariable UUID clientId,
            @Parameter(description = "Filter to a single fund")
            @RequestParam(required = false) UUID fundId,
            @Parameter(description = "Filter to a single investor")
            @RequestParam(required = false) UUID investorId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                transactionService.list(clientId, fundId, investorId, pageable), response -> response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction by id")
    public TransactionDtos.Response get(@PathVariable UUID clientId, @PathVariable UUID transactionId) {
        return transactionService.get(clientId, transactionId);
    }

    @PutMapping("/{transactionId}")
    @Operation(summary = "Correct a transaction's type, amount, date or notes")
    public TransactionDtos.Response update(@PathVariable UUID clientId,
                                           @PathVariable UUID transactionId,
                                           @Valid @RequestBody TransactionDtos.UpdateRequest request) {
        return transactionService.update(clientId, transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID transactionId) {
        transactionService.delete(clientId, transactionId);
        return ResponseEntity.noContent().build();
    }
}
