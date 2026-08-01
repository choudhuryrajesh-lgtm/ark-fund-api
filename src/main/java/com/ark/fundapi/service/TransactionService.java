package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.domain.Fund;
import com.ark.fundapi.domain.Investor;
import com.ark.fundapi.domain.Transaction;
import com.ark.fundapi.domain.TransactionType;
import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.TransactionRepository;
import com.ark.fundapi.web.dto.TransactionDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final int MONEY_SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final FundService fundService;
    private final InvestorService investorService;
    private final TransactionTypeService transactionTypeService;

    public TransactionService(TransactionRepository transactionRepository,
                              ClientService clientService,
                              FundService fundService,
                              InvestorService investorService,
                              TransactionTypeService transactionTypeService) {
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
        this.fundService = fundService;
        this.investorService = investorService;
        this.transactionTypeService = transactionTypeService;
    }

    @Transactional
    public TransactionDtos.Response create(UUID clientId, TransactionDtos.CreateRequest request) {
        Client client = clientService.require(clientId);

        // Resolving both through their tenant-scoped loaders is what enforces
        // that a fund and an investor from two different clients can never be
        // linked by a transaction — the cross-tenant leak this API most needs
        // to prevent.
        Fund fund = fundService.require(clientId, request.fundId());
        Investor investor = investorService.require(clientId, request.investorId());
        TransactionType type = transactionTypeService.require(request.type());

        validateTransactionDate(request.transactionDate(), fund);

        Transaction transaction = new Transaction(
                client,
                fund,
                investor,
                type,
                normalize(request.amount()),
                request.transactionDate(),
                request.notes()
        );
        return TransactionDtos.Response.from(transactionRepository.save(transaction));
    }

    public Page<TransactionDtos.Response> list(UUID clientId, UUID fundId, UUID investorId, Pageable pageable) {
        clientService.require(clientId);

        Page<Transaction> page;
        if (fundId != null) {
            fundService.require(clientId, fundId);
            page = transactionRepository.findByClientIdAndFundId(clientId, fundId, pageable);
        } else if (investorId != null) {
            investorService.require(clientId, investorId);
            page = transactionRepository.findByClientIdAndInvestorId(clientId, investorId, pageable);
        } else {
            page = transactionRepository.findByClientId(clientId, pageable);
        }
        return page.map(TransactionDtos.Response::from);
    }

    public TransactionDtos.Response get(UUID clientId, UUID transactionId) {
        return TransactionDtos.Response.from(require(clientId, transactionId));
    }

    @Transactional
    public TransactionDtos.Response update(UUID clientId, UUID transactionId, TransactionDtos.UpdateRequest request) {
        Transaction transaction = require(clientId, transactionId);
        validateTransactionDate(request.transactionDate(), transaction.getFund());

        transaction.setType(transactionTypeService.require(request.type()));
        transaction.setAmount(normalize(request.amount()));
        transaction.setTransactionDate(request.transactionDate());
        transaction.setNotes(request.notes());
        return TransactionDtos.Response.from(transaction);
    }

    @Transactional
    public void delete(UUID clientId, UUID transactionId) {
        transactionRepository.delete(require(clientId, transactionId));
    }

    private Transaction require(UUID clientId, UUID transactionId) {
        clientService.require(clientId);
        return transactionRepository.findByIdAndClientId(transactionId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    private void validateTransactionDate(LocalDate transactionDate, Fund fund) {
        // A transaction cannot predate the fund it belongs to; that would place
        // money in a vehicle that did not exist yet and skew any as-of report
        // run against an earlier date.
        if (transactionDate.isBefore(fund.getInceptionDate())) {
            throw new BusinessRuleException(
                    "Transaction date %s is before the fund's inception date %s"
                            .formatted(transactionDate, fund.getInceptionDate()));
        }
    }

    /**
     * Pins amounts to two decimal places on the way in, so the value stored is
     * exactly the value reported. Rejecting extra precision rather than
     * silently rounding it is arguably safer still, but bean validation already
     * caps input at two fraction digits — this guards the scale of the stored
     * BigDecimal so sums never carry a trailing-scale surprise.
     */
    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
