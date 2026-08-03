package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.domain.Fund;
import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.FundRepository;
import com.ark.fundapi.repository.TransactionRepository;
import com.ark.fundapi.web.dto.FundDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Fund CRUD, scoped to its owning client throughout — see {@link #require}. */
@Service
@Transactional(readOnly = true)
public class FundService {

    private static final Logger log = LoggerFactory.getLogger(FundService.class);

    private final FundRepository fundRepository;
    private final TransactionRepository transactionRepository;
    private final ClientService clientService;

    public FundService(FundRepository fundRepository,
                       TransactionRepository transactionRepository,
                       ClientService clientService) {
        this.fundRepository = fundRepository;
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
    }

    @Transactional
    public FundDtos.Response create(UUID clientId, FundDtos.CreateRequest request) {
        Client client = clientService.require(clientId);
        String name = request.name().trim();
        if (fundRepository.existsByClientIdAndNameIgnoreCase(clientId, name)) {
            log.warn("Rejected fund creation for client {}: name already in use", clientId);
            throw new BusinessRuleException("A fund named '%s' already exists for this client".formatted(name));
        }
        Fund fund = new Fund(client, name, trimToNull(request.description()), request.inceptionDate());
        fund = fundRepository.save(fund);
        log.info("Created fund {} for client {}", fund.getId(), clientId);
        return FundDtos.Response.from(fund);
    }

    public Page<FundDtos.Response> list(UUID clientId, Pageable pageable) {
        clientService.require(clientId);
        return fundRepository.findByClientId(clientId, pageable).map(FundDtos.Response::from);
    }

    public FundDtos.Response get(UUID clientId, UUID fundId) {
        return FundDtos.Response.from(require(clientId, fundId));
    }

    @Transactional
    public FundDtos.Response update(UUID clientId, UUID fundId, FundDtos.UpdateRequest request) {
        Fund fund = require(clientId, fundId);
        String name = request.name().trim();
        if (fundRepository.existsByClientIdAndNameIgnoreCaseAndIdNot(clientId, name, fundId)) {
            log.warn("Rejected update for fund {}: name already in use", fundId);
            throw new BusinessRuleException("A fund named '%s' already exists for this client".formatted(name));
        }
        fund.setName(name);
        fund.setDescription(trimToNull(request.description()));
        fund.setInceptionDate(request.inceptionDate());
        log.info("Updated fund {}", fundId);
        return FundDtos.Response.from(fund);
    }

    @Transactional
    public void delete(UUID clientId, UUID fundId) {
        Fund fund = require(clientId, fundId);
        // A fund with transactions is a financial record, not a typo. Deleting
        // it would orphan investor history, so this is refused outright —
        // closing a fund is a lifecycle change (a status field), not a delete.
        if (transactionRepository.existsByFundId(fundId)) {
            log.warn("Rejected deletion of fund {}: has existing transactions", fundId);
            throw new BusinessRuleException(
                    "Fund cannot be deleted because it has transactions. Transactions must be removed first.");
        }
        fundRepository.delete(fund);
        log.info("Deleted fund {}", fundId);
    }

    /** Loads a fund scoped to its client, or throws 404. */
    public Fund require(UUID clientId, UUID fundId) {
        clientService.require(clientId);
        return fundRepository.findByIdAndClientId(fundId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Fund", fundId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
