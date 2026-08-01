package com.ark.fundapi.repository;

import com.ark.fundapi.domain.Transaction;
import com.ark.fundapi.repository.projection.FundInvestorCount;
import com.ark.fundapi.repository.projection.PartyTypeTotal;
import com.ark.fundapi.repository.projection.TypeTotal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndClientId(UUID id, UUID clientId);

    Page<Transaction> findByClientId(UUID clientId, Pageable pageable);

    Page<Transaction> findByClientIdAndFundId(UUID clientId, UUID fundId, Pageable pageable);

    Page<Transaction> findByClientIdAndInvestorId(UUID clientId, UUID investorId, Pageable pageable);

    // ---------------------------------------------------------------------
    // Reporting aggregates.
    //
    // These roll up in the database rather than loading the ledger into memory
    // and summing in Java. A fund with a million transactions still returns a
    // handful of rows — one per transaction type.
    //
    // `asOfDate` is always supplied (the service defaults it to a far-future
    // sentinel) rather than being nullable. A nullable date parameter forces
    // an `IS NULL OR` predicate that defeats index usage and needs explicit
    // casting on some drivers.
    // ---------------------------------------------------------------------

    @Query("""
            SELECT new com.ark.fundapi.repository.projection.TypeTotal(t.type.code, SUM(t.amount))
            FROM Transaction t
            WHERE t.fund.id = :fundId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.type.code
            """)
    List<TypeTotal> totalsByTypeForFund(@Param("fundId") UUID fundId,
                                        @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT new com.ark.fundapi.repository.projection.TypeTotal(t.type.code, SUM(t.amount))
            FROM Transaction t
            WHERE t.investor.id = :investorId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.type.code
            """)
    List<TypeTotal> totalsByTypeForInvestor(@Param("investorId") UUID investorId,
                                            @Param("asOfDate") LocalDate asOfDate);

    /** Per-investor breakdown within one fund — powers the fund report's investor positions. */
    @Query("""
            SELECT new com.ark.fundapi.repository.projection.PartyTypeTotal(
                       t.investor.id, t.investor.name, t.type.code, SUM(t.amount))
            FROM Transaction t
            WHERE t.fund.id = :fundId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.investor.id, t.investor.name, t.type.code
            """)
    List<PartyTypeTotal> investorTotalsForFund(@Param("fundId") UUID fundId,
                                               @Param("asOfDate") LocalDate asOfDate);

    /** Per-fund breakdown for one investor — powers the investor report's fund positions. */
    @Query("""
            SELECT new com.ark.fundapi.repository.projection.PartyTypeTotal(
                       t.fund.id, t.fund.name, t.type.code, SUM(t.amount))
            FROM Transaction t
            WHERE t.investor.id = :investorId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.fund.id, t.fund.name, t.type.code
            """)
    List<PartyTypeTotal> fundTotalsForInvestor(@Param("investorId") UUID investorId,
                                               @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT COUNT(DISTINCT t.investor.id)
            FROM Transaction t
            WHERE t.fund.id = :fundId
              AND t.transactionDate <= :asOfDate
            """)
    long countDistinctInvestorsInFund(@Param("fundId") UUID fundId,
                                      @Param("asOfDate") LocalDate asOfDate);

    // ---------------------------------------------------------------------
    // Client-wide portfolio rollup. Two grouped queries cover every fund the
    // client operates, rather than looping per fund (N+1).
    // ---------------------------------------------------------------------

    @Query("""
            SELECT new com.ark.fundapi.repository.projection.PartyTypeTotal(
                       t.fund.id, t.fund.name, t.type.code, SUM(t.amount))
            FROM Transaction t
            WHERE t.client.id = :clientId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.fund.id, t.fund.name, t.type.code
            """)
    List<PartyTypeTotal> fundTotalsForClient(@Param("clientId") UUID clientId,
                                             @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT new com.ark.fundapi.repository.projection.FundInvestorCount(
                       t.fund.id, COUNT(DISTINCT t.investor.id))
            FROM Transaction t
            WHERE t.client.id = :clientId
              AND t.transactionDate <= :asOfDate
            GROUP BY t.fund.id
            """)
    List<FundInvestorCount> investorCountsByFundForClient(@Param("clientId") UUID clientId,
                                                          @Param("asOfDate") LocalDate asOfDate);

    boolean existsByFundId(UUID fundId);

    boolean existsByInvestorId(UUID investorId);
}
