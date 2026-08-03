package com.ark.fundapi.web;

import com.ark.fundapi.service.ClientService;
import com.ark.fundapi.web.dto.ClientDtos;
import com.ark.fundapi.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** REST endpoints for {@code /api/v1/clients} — the top-level tenant resource. */
@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Platform clients (1)")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** Creates a new client. Returns 201 with a {@code Location} header pointing at the new resource. */
    @PostMapping
    @Operation(summary = "Create a client")
    public ResponseEntity<ClientDtos.Response> create(@Valid @RequestBody ClientDtos.CreateRequest request) {
        log.info("POST /clients");
        ClientDtos.Response created = clientService.create(request);
        // 201 with a Location header, per REST convention — the caller gets the
        // canonical URI of what it just created without having to build it.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Lists clients, paginated. */
    @GetMapping
    @Operation(summary = "List clients")
    public PageResponse<ClientDtos.Response> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("GET /clients page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(clientService.list(pageable), response -> response);
    }

    /** Fetches a single client by id, or 404 if it doesn't exist. */
    @GetMapping("/{clientId}")
    @Operation(summary = "Get a client by id")
    public ClientDtos.Response get(@PathVariable UUID clientId) {
        log.debug("GET /clients/{}", clientId);
        return clientService.get(clientId);
    }

    /** Updates a client's name/email. */
    @PutMapping("/{clientId}")
    @Operation(summary = "Update a client")
    public ClientDtos.Response update(@PathVariable UUID clientId,
                                      @Valid @RequestBody ClientDtos.UpdateRequest request) {
        log.info("PUT /clients/{}", clientId);
        return clientService.update(clientId, request);
    }

    /** Deletes a client. Fails if any fund, investor or transaction still references it. */
    @DeleteMapping("/{clientId}")
    @Operation(summary = "Delete a client")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId) {
        log.info("DELETE /clients/{}", clientId);
        clientService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
