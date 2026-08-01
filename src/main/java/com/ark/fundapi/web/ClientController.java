package com.ark.fundapi.web;

import com.ark.fundapi.service.ClientService;
import com.ark.fundapi.web.dto.ClientDtos;
import com.ark.fundapi.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Platform clients (1)")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    @Operation(summary = "Create a client")
    public ResponseEntity<ClientDtos.Response> create(@Valid @RequestBody ClientDtos.CreateRequest request) {
        ClientDtos.Response created = clientService.create(request);
        // 201 with a Location header, per REST convention — the caller gets the
        // canonical URI of what it just created without having to build it.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List clients")
    public PageResponse<ClientDtos.Response> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(clientService.list(pageable), response -> response);
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get a client by id")
    public ClientDtos.Response get(@PathVariable UUID clientId) {
        return clientService.get(clientId);
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Update a client")
    public ClientDtos.Response update(@PathVariable UUID clientId,
                                      @Valid @RequestBody ClientDtos.UpdateRequest request) {
        return clientService.update(clientId, request);
    }

    @DeleteMapping("/{clientId}")
    @Operation(summary = "Delete a client")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId) {
        clientService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
