package com.ark.fundapi.web;

import com.ark.fundapi.service.InvestorService;
import com.ark.fundapi.web.dto.InvestorDtos;
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
@RequestMapping("/api/v1/clients/{clientId}/investors")
@Tag(name = "Investors", description = "Investors belonging to a client")
public class InvestorController {

    private final InvestorService investorService;

    public InvestorController(InvestorService investorService) {
        this.investorService = investorService;
    }

    @PostMapping
    @Operation(summary = "Create an investor for a client")
    public ResponseEntity<InvestorDtos.Response> create(@PathVariable UUID clientId,
                                                        @Valid @RequestBody InvestorDtos.CreateRequest request) {
        InvestorDtos.Response created = investorService.create(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List a client's investors")
    public PageResponse<InvestorDtos.Response> list(
            @PathVariable UUID clientId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(investorService.list(clientId, pageable), response -> response);
    }

    @GetMapping("/{investorId}")
    @Operation(summary = "Get an investor by id")
    public InvestorDtos.Response get(@PathVariable UUID clientId, @PathVariable UUID investorId) {
        return investorService.get(clientId, investorId);
    }

    @PutMapping("/{investorId}")
    @Operation(summary = "Update an investor")
    public InvestorDtos.Response update(@PathVariable UUID clientId,
                                        @PathVariable UUID investorId,
                                        @Valid @RequestBody InvestorDtos.UpdateRequest request) {
        return investorService.update(clientId, investorId, request);
    }

    @DeleteMapping("/{investorId}")
    @Operation(summary = "Delete an investor (rejected if they have transactions)")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID investorId) {
        investorService.delete(clientId, investorId);
        return ResponseEntity.noContent().build();
    }
}
