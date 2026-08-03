package com.ark.fundapi.web;

import com.ark.fundapi.service.ReportingService;
import com.ark.fundapi.web.dto.ReportDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reporting endpoints.
 *
 * <p>Every report accepts an optional {@code asOfDate}. Fund accounting is
 * routinely restated — back-dated transactions arrive after a period has been
 * reported on — so being able to ask "what did this look like on 30 June" is a
 * baseline requirement, not an enhancement.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/reports")
@Tag(name = "Reports", description = "Fund, investor and portfolio reporting")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportingService reportingService;

    public ReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /** Fund report: net balance, totals by transaction type, and every investor's position in the fund. */
    @GetMapping("/funds/{fundId}")
    @Operation(summary = "Fund report: balance, totals by transaction type, and per-investor positions")
    public ReportDtos.FundReport fundReport(
            @PathVariable UUID clientId,
            @PathVariable UUID fundId,
            @Parameter(description = "Include only transactions on or before this date (defaults to all)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("GET /clients/{}/reports/funds/{} asOfDate={}", clientId, fundId, asOfDate);
        return reportingService.fundReport(clientId, fundId, asOfDate);
    }

    /** Investor report: total position and a breakdown across every fund the investor participates in. */
    @GetMapping("/investors/{investorId}")
    @Operation(summary = "Investor report: total position and a breakdown across every fund they participate in")
    public ReportDtos.InvestorReport investorReport(
            @PathVariable UUID clientId,
            @PathVariable UUID investorId,
            @Parameter(description = "Include only transactions on or before this date (defaults to all)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("GET /clients/{}/reports/investors/{} asOfDate={}", clientId, investorId, asOfDate);
        return reportingService.investorReport(clientId, investorId, asOfDate);
    }

    /** Client portfolio report: rolled-up totals across every fund the client owns. */
    @GetMapping("/portfolio")
    @Operation(summary = "Client portfolio report: rolled-up totals across every fund")
    public ReportDtos.ClientPortfolioReport portfolioReport(
            @PathVariable UUID clientId,
            @Parameter(description = "Include only transactions on or before this date (defaults to all)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("GET /clients/{}/reports/portfolio asOfDate={}", clientId, asOfDate);
        return reportingService.portfolioReport(clientId, asOfDate);
    }
}
