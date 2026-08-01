package com.ark.fundapi.web;

import com.ark.fundapi.service.FundService;
import com.ark.fundapi.web.dto.FundDtos;
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

/**
 * Funds are nested under their client so tenancy is explicit in the URL. Every
 * handler is scoped to {@code clientId}, and requesting a fund belonging to a
 * different client returns 404 rather than confirming it exists.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/funds")
@Tag(name = "Funds", description = "Investment funds belonging to a client")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @PostMapping
    @Operation(summary = "Create a fund for a client")
    public ResponseEntity<FundDtos.Response> create(@PathVariable UUID clientId,
                                                    @Valid @RequestBody FundDtos.CreateRequest request) {
        FundDtos.Response created = fundService.create(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List a client's funds")
    public PageResponse<FundDtos.Response> list(
            @PathVariable UUID clientId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(fundService.list(clientId, pageable), response -> response);
    }

    @GetMapping("/{fundId}")
    @Operation(summary = "Get a fund by id")
    public FundDtos.Response get(@PathVariable UUID clientId, @PathVariable UUID fundId) {
        return fundService.get(clientId, fundId);
    }

    @PutMapping("/{fundId}")
    @Operation(summary = "Update a fund")
    public FundDtos.Response update(@PathVariable UUID clientId,
                                    @PathVariable UUID fundId,
                                    @Valid @RequestBody FundDtos.UpdateRequest request) {
        return fundService.update(clientId, fundId, request);
    }

    @DeleteMapping("/{fundId}")
    @Operation(summary = "Delete a fund (rejected if it has transactions)")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID fundId) {
        fundService.delete(clientId, fundId);
        return ResponseEntity.noContent().build();
    }
}
