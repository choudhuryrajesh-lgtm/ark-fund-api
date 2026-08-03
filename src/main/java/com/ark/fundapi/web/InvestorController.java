package com.ark.fundapi.web;

import com.ark.fundapi.service.InvestorService;
import com.ark.fundapi.web.dto.InvestorDtos;
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

@RestController
@RequestMapping("/api/v1/clients/{clientId}/investors")
@Tag(name = "Investors", description = "Investors belonging to a client")
public class InvestorController {

    private static final Logger log = LoggerFactory.getLogger(InvestorController.class);

    private final InvestorService investorService;

    public InvestorController(InvestorService investorService) {
        this.investorService = investorService;
    }

    /** Creates an investor under the given client. Returns 201 with a {@code Location} header. */
    @PostMapping
    @Operation(summary = "Create an investor for a client")
    public ResponseEntity<InvestorDtos.Response> create(@PathVariable UUID clientId,
                                                        @Valid @RequestBody InvestorDtos.CreateRequest request) {
        log.info("POST /clients/{}/investors", clientId);
        InvestorDtos.Response created = investorService.create(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Lists a client's investors, paginated. */
    @GetMapping
    @Operation(summary = "List a client's investors")
    public PageResponse<InvestorDtos.Response> list(
            @PathVariable UUID clientId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("GET /clients/{}/investors page={} size={}", clientId, pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(investorService.list(clientId, pageable), response -> response);
    }

    /** Fetches a single investor by id, scoped to their client, or 404 if they don't exist under that client. */
    @GetMapping("/{investorId}")
    @Operation(summary = "Get an investor by id")
    public InvestorDtos.Response get(@PathVariable UUID clientId, @PathVariable UUID investorId) {
        log.debug("GET /clients/{}/investors/{}", clientId, investorId);
        return investorService.get(clientId, investorId);
    }

    /** Updates an investor's name/email. */
    @PutMapping("/{investorId}")
    @Operation(summary = "Update an investor")
    public InvestorDtos.Response update(@PathVariable UUID clientId,
                                        @PathVariable UUID investorId,
                                        @Valid @RequestBody InvestorDtos.UpdateRequest request) {
        log.info("PUT /clients/{}/investors/{}", clientId, investorId);
        return investorService.update(clientId, investorId, request);
    }

    /** Deletes an investor. Fails if they still have transactions recorded against them. */
    @DeleteMapping("/{investorId}")
    @Operation(summary = "Delete an investor (rejected if they have transactions)")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID investorId) {
        log.info("DELETE /clients/{}/investors/{}", clientId, investorId);
        investorService.delete(clientId, investorId);
        return ResponseEntity.noContent().build();
    }
}
