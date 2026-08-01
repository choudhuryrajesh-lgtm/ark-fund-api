package com.ark.fundapi.web;

import com.ark.fundapi.service.TransactionTypeService;
import com.ark.fundapi.web.dto.TransactionTypeDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Transaction types are reference data governed centrally, not per client —
 * unlike funds, investors and transactions, they are not nested under
 * {@code /clients/{clientId}}. A client picks from this list when posting a
 * transaction; adding a new type is an operational change to this table, not
 * a client self-service action, which keeps the ledger's categories under
 * one controlled vocabulary across every tenant.
 */
@RestController
@RequestMapping("/api/v1/transaction-types")
@Tag(name = "Transaction Types", description = "The credit/debit-classified types a transaction may be posted with")
public class TransactionTypeController {

    private final TransactionTypeService transactionTypeService;

    public TransactionTypeController(TransactionTypeService transactionTypeService) {
        this.transactionTypeService = transactionTypeService;
    }

    @GetMapping
    @Operation(summary = "List transaction types available for posting a new transaction")
    public List<TransactionTypeDtos.Response> list() {
        return transactionTypeService.listActive().stream()
                .map(TransactionTypeDtos.Response::from)
                .toList();
    }
}