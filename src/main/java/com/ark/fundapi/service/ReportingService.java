package com.ark.fundapi.service;

import com.ark.fundapi.domain.Client;
import com.ark.fundapi.domain.Fund;
import com.ark.fundapi.domain.Investor;
import com.ark.fundapi.domain.TransactionType;
import com.ark.fundapi.repository.FundRepository;
import com.ark.fundapi.repository.TransactionRepository;
import com.ark.fundapi.repository.TransactionTypeRepository;
import com.ark.fundapi.repository.projection.FundInvestorCount;
import com.ark.fundapi.repository.projection.PartyTypeTotal;
import com.ark.fundapi.repository.projection.TypeTotal;
import com.ark.fundapi.web.dto.ReportDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reporting for funds, investors and the client portfolio.
 *
 * <p>All aggregation happens in the database; this class only assembles the
 * grouped rows into report shapes and applies the credit/debit rule, which
 * lives on {@link TransactionType} rather than being restated here.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    /**
     * Stand-in for "no upper bound" on as-of queries. A concrete far-future
     * date keeps the SQL predicate index-friendly and avoids a nullable
     * parameter; {@code LocalDate.MAX} is not used because its year exceeds
     * what PostgreSQL's DATE type can represent.
     */
    private static final LocalDate NO_DATE_LIMIT = LocalDate.of(9999, 12, 31);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TransactionRepository transactionRepository;
    private final FundRepository fundRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final ClientService clientService;
    private final FundService fundService;
    private final InvestorService investorService;

    public ReportingService(TransactionRepository transactionRepository,
                            FundRepository fundRepository,
                            TransactionTypeRepository transactionTypeRepository,
                            ClientService clientService,
                            FundService fundService,
                            InvestorService investorService) {
        this.transactionRepository = transactionRepository;
        this.fundRepository = fundRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.clientService = clientService;
        this.fundService = fundService;
        this.investorService = investorService;
    }

    // ------------------------------------------------------------------
    // Fund report
    // ------------------------------------------------------------------

    public ReportDtos.FundReport fundReport(UUID clientId, UUID fundId, LocalDate asOfDate) {
        Fund fund = fundService.require(clientId, fundId);
        LocalDate effectiveDate = resolve(asOfDate);
        List<TransactionType> allTypes = transactionTypeRepository.findAllByOrderByCodeAsc();

        ReportDtos.Totals totals = toTotals(transactionRepository.totalsByTypeForFund(fundId, effectiveDate), allTypes);
        List<ReportDtos.InvestorPosition> positions =
                transactionRepository.investorTotalsForFund(fundId, effectiveDate).stream()
                        .collect(Collectors.groupingBy(PartyTypeTotal::partyId, LinkedHashMap::new, Collectors.toList()))
                        .values().stream()
                        .map(rows -> new ReportDtos.InvestorPosition(
                                rows.get(0).partyId(),
                                rows.get(0).partyName(),
                                toTotalsFromParty(rows, allTypes)))
                        .sorted((a, b) -> a.investorName().compareToIgnoreCase(b.investorName()))
                        .toList();

        return new ReportDtos.FundReport(
                fund.getId(),
                fund.getName(),
                fund.getInceptionDate(),
                effectiveDate.equals(NO_DATE_LIMIT) ? LocalDate.now() : effectiveDate,
                positions.size(),
                totals,
                positions
        );
    }

    // ------------------------------------------------------------------
    // Investor report
    // ------------------------------------------------------------------

    public ReportDtos.InvestorReport investorReport(UUID clientId, UUID investorId, LocalDate asOfDate) {
        Investor investor = investorService.require(clientId, investorId);
        LocalDate effectiveDate = resolve(asOfDate);
        List<TransactionType> allTypes = transactionTypeRepository.findAllByOrderByCodeAsc();

        ReportDtos.Totals totals = toTotals(transactionRepository.totalsByTypeForInvestor(investorId, effectiveDate), allTypes);
        List<ReportDtos.FundPosition> positions =
                transactionRepository.fundTotalsForInvestor(investorId, effectiveDate).stream()
                        .collect(Collectors.groupingBy(PartyTypeTotal::partyId, LinkedHashMap::new, Collectors.toList()))
                        .values().stream()
                        .map(rows -> new ReportDtos.FundPosition(
                                rows.get(0).partyId(),
                                rows.get(0).partyName(),
                                toTotalsFromParty(rows, allTypes)))
                        .sorted((a, b) -> a.fundName().compareToIgnoreCase(b.fundName()))
                        .toList();

        return new ReportDtos.InvestorReport(
                investor.getId(),
                investor.getName(),
                effectiveDate.equals(NO_DATE_LIMIT) ? LocalDate.now() : effectiveDate,
                positions.size(),
                totals,
                positions
        );
    }

    // ------------------------------------------------------------------
    // Client portfolio report
    // ------------------------------------------------------------------

    public ReportDtos.ClientPortfolioReport portfolioReport(UUID clientId, LocalDate asOfDate) {
        Client client = clientService.require(clientId);
        LocalDate effectiveDate = resolve(asOfDate);
        List<TransactionType> allTypes = transactionTypeRepository.findAllByOrderByCodeAsc();

        Map<UUID, List<PartyTypeTotal>> totalsByFund =
                transactionRepository.fundTotalsForClient(clientId, effectiveDate).stream()
                        .collect(Collectors.groupingBy(PartyTypeTotal::partyId));

        Map<UUID, Long> investorCounts =
                transactionRepository.investorCountsByFundForClient(clientId, effectiveDate).stream()
                        .collect(Collectors.toMap(FundInvestorCount::fundId, FundInvestorCount::investorCount));

        // Driven off the fund list rather than the transaction rollup so funds
        // with no activity yet still appear (at zero) instead of vanishing.
        List<Fund> funds = fundRepository.findByClientIdOrderByNameAsc(clientId);

        List<ReportDtos.ClientPortfolioReport.FundSummary> summaries = new ArrayList<>();
        Map<String, BigDecimal> portfolioByType = emptyAmountMap(allTypes);

        for (Fund fund : funds) {
            List<PartyTypeTotal> rows = totalsByFund.getOrDefault(fund.getId(), List.of());
            ReportDtos.Totals fundTotals = toTotalsFromParty(rows, allTypes);

            fundTotals.byType().forEach((type, amount) ->
                    portfolioByType.merge(type, amount, BigDecimal::add));

            summaries.add(new ReportDtos.ClientPortfolioReport.FundSummary(
                    fund.getId(),
                    fund.getName(),
                    investorCounts.getOrDefault(fund.getId(), 0L).intValue(),
                    fundTotals.netBalance()
            ));
        }

        return new ReportDtos.ClientPortfolioReport(
                client.getId(),
                client.getName(),
                effectiveDate.equals(NO_DATE_LIMIT) ? LocalDate.now() : effectiveDate,
                summaries.size(),
                buildTotals(portfolioByType, allTypes),
                summaries
        );
    }

    // ------------------------------------------------------------------
    // Aggregation helpers
    // ------------------------------------------------------------------

    private ReportDtos.Totals toTotals(Collection<TypeTotal> rows, List<TransactionType> allTypes) {
        Map<String, BigDecimal> byType = emptyAmountMap(allTypes);
        rows.forEach(row -> byType.put(row.type(), scaled(row.total())));
        return buildTotals(byType, allTypes);
    }

    private ReportDtos.Totals toTotalsFromParty(Collection<PartyTypeTotal> rows, List<TransactionType> allTypes) {
        Map<String, BigDecimal> byType = emptyAmountMap(allTypes);
        rows.forEach(row -> byType.put(row.type(), scaled(row.total())));
        return buildTotals(byType, allTypes);
    }

    /**
     * Applies the credit/debit rule. Every known transaction type is
     * represented — including retired ones and ones with no activity — so
     * clients can render a report without null-checking each type.
     */
    private ReportDtos.Totals buildTotals(Map<String, BigDecimal> byType, List<TransactionType> allTypes) {
        Map<String, TransactionType> typesByCode = allTypes.stream()
                .collect(Collectors.toMap(TransactionType::getCode, type -> type));

        BigDecimal credits = ZERO;
        BigDecimal debits = ZERO;

        for (Map.Entry<String, BigDecimal> entry : byType.entrySet()) {
            TransactionType type = typesByCode.get(entry.getKey());
            if (type != null && type.isCredit()) {
                credits = credits.add(entry.getValue());
            } else {
                debits = debits.add(entry.getValue());
            }
        }
        return new ReportDtos.Totals(byType, credits, debits, credits.subtract(debits));
    }

    /** Seeded with every known type at zero, ordered by code. */
    private static Map<String, BigDecimal> emptyAmountMap(List<TransactionType> allTypes) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (TransactionType type : allTypes) {
            map.put(type.getCode(), ZERO);
        }
        return map;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDate resolve(LocalDate asOfDate) {
        return asOfDate == null ? NO_DATE_LIMIT : asOfDate;
    }
}
