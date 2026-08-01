package com.ark.fundapi.repository.projection;

import java.math.BigDecimal;

/**
 * Aggregated transaction total for a single transaction type.
 *
 * <p>{@code type} is the transaction type's code (e.g. {@code "CONTRIBUTION"}),
 * not the entity — projections stay flat so a report query never triggers a
 * lazy load of {@code transaction_types} per row.
 */
public record TypeTotal(String type, BigDecimal total) {
}