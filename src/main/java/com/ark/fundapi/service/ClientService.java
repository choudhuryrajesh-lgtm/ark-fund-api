package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.ClientRepository;
import com.ark.fundapi.web.dto.ClientDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientDtos.Response create(ClientDtos.CreateRequest request) {
        if (clientRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("A client already exists with email " + request.email());
}
        Client client = new Client(request.name().trim(), request.email().trim().toLowerCase());
        return ClientDtos.Response.from(clientRepository.save(client));
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
            throw new BusinessRuleException("A client already exists with email " + email);
        }
        client.setName(request.name().trim());
        client.setEmail(email);
        return ClientDtos.Response.from(client);
    }

    @Transactional
    public void delete(UUID clientId) {
        Client client = require(clientId);
        // Funds, investors and transactions all reference the client, so the
        // database FKs would reject this anyway. Failing here gives a clear
        // business message instead of a raw constraint-violation stack trace.
        clientRepository.delete(client);
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
