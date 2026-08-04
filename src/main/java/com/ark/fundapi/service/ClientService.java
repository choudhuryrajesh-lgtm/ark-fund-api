package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.ClientRepository;
import com.ark.fundapi.repository.FundRepository;
import com.ark.fundapi.repository.InvestorRepository;
import com.ark.fundapi.web.dto.ClientDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Client (tenant) lifecycle. Every other service depends on {@link #require}
 * to enforce tenant existence in one place rather than re-checking it
 * per-repository.
 */
@Service
@Transactional(readOnly = true)
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;
    private final FundRepository fundRepository;
    private final InvestorRepository investorRepository;

    public ClientService(ClientRepository clientRepository,
                         FundRepository fundRepository,
                         InvestorRepository investorRepository) {
        this.clientRepository = clientRepository;
        this.fundRepository = fundRepository;
        this.investorRepository = investorRepository;
    }

    @Transactional
    public ClientDtos.Response create(ClientDtos.CreateRequest request) {
        if (clientRepository.existsByEmailIgnoreCase(request.email())) {
            log.warn("Rejected client creation: email already registered");
            throw new BusinessRuleException("A client already exists1 with email " + request.email());
        }
        Client client = new Client(request.name().trim(), request.email().trim().toLowerCase());
        client = clientRepository.save(client);
        log.info("Created client {}", client.getId());
        return ClientDtos.Response.from(client);
    }

    public Page<ClientDtos.Response> list(Pageable pageable) {
        return clientRepository.findAll(pageable).map(ClientDtos.Response::from);
    }

    public ClientDtos.Response get(UUID clientId) {
        return ClientDtos.Response.from(require(clientId));
    }

    @Transactional
    public ClientDtos.Response update(UUID clientId, ClientDtos.UpdateRequest request) {
        Client client = require(clientId);
        String email = request.email().trim().toLowerCase();
        if (!client.getEmail().equalsIgnoreCase(email) && clientRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Rejected update for client {}: email already registered", clientId);
            throw new BusinessRuleException("A client already exists with email " + email);
        }
        client.setName(request.name().trim());
        client.setEmail(email);
        log.info("Updated client {}", clientId);
        return ClientDtos.Response.from(client);
    }

    @Transactional
    public void delete(UUID clientId) {
        Client client = require(clientId);
        // Funds, investors and transactions all reference the client with
        // plain foreign keys — no ON DELETE CASCADE (V1__initial_schema.sql).
        // Without this check the delete reaches the database and comes back as
        // a constraint violation, which surfaces to the caller as a 500 rather
        // than as the business rule it actually is. Checking here turns it into
        // a 409 that says what to do about it — the same treatment
        // FundService and InvestorService give their own dependants.
        //
        // Deliberately not a cascade: a tenant's whole ledger disappearing on
        // one DELETE is not a safe default for financial records.
        if (fundRepository.existsByClientId(clientId)) {
            log.warn("Rejected deletion of client {}: has existing funds", clientId);
            throw new BusinessRuleException(
                    "Client cannot be deleted because it has funds. Delete its funds first.");
        }
        if (investorRepository.existsByClientId(clientId)) {
            log.warn("Rejected deletion of client {}: has existing investors", clientId);
            throw new BusinessRuleException(
                    "Client cannot be deleted because it has investors. Delete its investors first.");
        }
        clientRepository.delete(client);
        log.info("Deleted client {}", clientId);
    }

    /**
     * Loads a client or throws. Shared by the other services so tenant
     * existence is enforced in exactly one place.
     */
    public Client require(UUID clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
    }
}
