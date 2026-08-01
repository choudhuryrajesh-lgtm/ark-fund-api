package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.domain.Investor;
import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.InvestorRepository;
import com.ark.fundapi.repository.TransactionRepository;
import com.ark.fundapi.web.dto.InvestorDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InvestorService {

    private final InvestorRepository investorRepository;
    private final TransactionRepository transactionRepository;
    private final ClientService clientService;

    public InvestorService(InvestorRepository investorRepository,
                           TransactionRepository transactionRepository,
                           ClientService clientService) {
        this.investorRepository = investorRepository;
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
    }

    @Transactional
    public InvestorDtos.Response create(UUID clientId, InvestorDtos.CreateRequest request) {
        Client client = clientService.require(clientId);
        String email = request.email().trim().toLowerCase();
        if (investorRepository.existsByClientIdAndEmailIgnoreCase(clientId, email)) {
            throw new BusinessRuleException("An investor already exists with email " + email);
        }
        Investor investor = new Investor(client, request.name().trim(), email);
        return InvestorDtos.Response.from(investorRepository.save(investor));
    }

    public Page<InvestorDtos.Response> list(UUID clientId, Pageable pageable) {
        clientService.require(clientId);
        return investorRepository.findByClientId(clientId, pageable).map(InvestorDtos.Response::from);
    }

    public InvestorDtos.Response get(UUID clientId, UUID investorId) {
        return InvestorDtos.Response.from(require(clientId, investorId));
    }

    @Transactional
    public InvestorDtos.Response update(UUID clientId, UUID investorId, InvestorDtos.UpdateRequest request) {
        Investor investor = require(clientId, investorId);
        String email = request.email().trim().toLowerCase();
        if (investorRepository.existsByClientIdAndEmailIgnoreCaseAndIdNot(clientId, email, investorId)) {
            throw new BusinessRuleException("An investor already exists with email " + email);
        }
        investor.setName(request.name().trim());
        investor.setEmail(email);
        return InvestorDtos.Response.from(investor);
    }

    @Transactional
    public void delete(UUID clientId, UUID investorId) {
        Investor investor = require(clientId, investorId);
        if (transactionRepository.existsByInvestorId(investorId)) {
            throw new BusinessRuleException(
                    "Investor cannot be deleted because they have transactions. Transactions must be removed first.");
        }
        investorRepository.delete(investor);
    }

    /** Loads an investor scoped to their client, or throws 404. */
    public Investor require(UUID clientId, UUID investorId) {
        clientService.require(clientId);
        return investorRepository.findByIdAndClientId(investorId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor", investorId));
    }
}
