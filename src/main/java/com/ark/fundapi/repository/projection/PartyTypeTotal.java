package com.ark.fundapi.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated total for one counterparty and transaction type — used for the
 * per-investor breakdown inside a fund report, and the per-fund breakdown
 * inside an investor report.
 *
 * <p>{@code type} is the transaction type's code, not the entity — see
 * {@link TypeTotal} for why. One grouped query produces the whole breakdown,
 * avoiding a per-party query loop (the classic N+1 in reporting code).
 */
public record PartyTypeTotal(UUID partyId, String partyName, String type, BigDecimal total) {
}