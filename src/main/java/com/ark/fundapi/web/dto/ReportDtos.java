package com.ark.fundapi.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reporting payloads.
 *
 * <p>Every report carries the {@code asOfDate} it was computed for. A balance
 * without an effective date is ambiguous the moment a back-dated transaction
 * arrives, and reports get screenshotted, emailed and filed — the date needs to
 * travel with the number.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** Credit/debit rollup shared by every report level. Keyed by transaction type code. */
    public record Totals(
            Map<String, BigDecimal> byType,
            BigDecimal totalCredits,
            BigDecimal totalDebits,
            BigDecimal netBalance
    ) {
    }

    /** One investor's position within a fund. */
    public record InvestorPosition(
            UUID investorId,
            String investorName,
            Totals totals
    ) {
    }

    /** One fund's position within an investor's portfolio. */
    public record FundPosition(
            UUID fundId,
            String fundName,
            Totals totals
    ) {
    }

    public record FundReport(
            UUID fundId,
            String fundName,
            LocalDate inceptionDate,
            LocalDate asOfDate,
            int investorCount,
            Totals totals,
            List<InvestorPosition> investorPositions
    ) {
    }

    public record InvestorReport(
            UUID investorId,
            String investorName,
            LocalDate asOfDate,
            int fundCount,
            Totals totals,
            List<FundPosition> fundPositions
    ) {
    }

    /** Portfolio-level view across every fund a client operates. */
    public record ClientPortfolioReport(
            UUID clientId,
            String clientName,
            LocalDate asOfDate,
            int fundCount,
            Totals totals,
            List<FundSummary> funds
    ) {
        public record FundSummary(
                UUID fundId,
                String fundName,
                int investorCount,
                BigDecimal netBalance
        ) {
        }
    }
}
