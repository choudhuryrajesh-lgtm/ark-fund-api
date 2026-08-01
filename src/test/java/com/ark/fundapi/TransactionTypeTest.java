package com.ark.fundapi;

import com.ark.fundapi.domain.TransactionDirection;
import com.ark.fundapi.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The credit/debit rule is the single most important piece of business logic in
 * the system — every reported balance depends on it — so it is pinned directly
 * rather than only being covered indirectly through the reporting tests.
 *
 * <p>{@link TransactionType} is a JPA entity backed by the {@code
 * transaction_types} table rather than a fixed enum, so these are plain
 * object tests of the credit/debit rule itself, not an enumeration over a
 * closed set of constants. The seeded set of types is verified separately in
 * {@link FundApiIntegrationTest} against a real (H2) database.
 */
class TransactionTypeTest {

    @Test
    void creditTypeIsCreditNotDebit() {
        TransactionType contribution = new TransactionType("CONTRIBUTION", TransactionDirection.CREDIT, "test");

        assertThat(contribution.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(contribution.isCredit()).isTrue();
        assertThat(contribution.isDebit()).isFalse();
    }

    @Test
    void debitTypeIsDebitNotCredit() {
        TransactionType managementFee = new TransactionType("MANAGEMENT_FEE", TransactionDirection.DEBIT, "test");

        assertThat(managementFee.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(managementFee.isDebit()).isTrue();
        assertThat(managementFee.isCredit()).isFalse();
    }

    @Test
    void creditsKeepTheirSignAndDebitsAreNegated() {
        BigDecimal amount = new BigDecimal("1000.00");
        TransactionType credit = new TransactionType("CONTRIBUTION", TransactionDirection.CREDIT, "test");
        TransactionType debit = new TransactionType("MANAGEMENT_FEE", TransactionDirection.DEBIT, "test");

        assertThat(credit.applySign(amount)).isEqualByComparingTo("1000.00");
        assertThat(debit.applySign(amount)).isEqualByComparingTo("-1000.00");
    }

    @Test
    void equalityIsByCode() {
        TransactionType first = new TransactionType("CONTRIBUTION", TransactionDirection.CREDIT, "one description");
        TransactionType second = new TransactionType("CONTRIBUTION", TransactionDirection.CREDIT, "a different description");

        // Same code must mean "the same type" regardless of what else differs —
        // this is what lets a report safely use a type as a map key.
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }
}