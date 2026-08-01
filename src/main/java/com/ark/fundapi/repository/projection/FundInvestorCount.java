package com.ark.fundapi.repository.projection;

import java.util.UUID;

/** Distinct investor count for a fund, used by the client portfolio report. */
public record FundInvestorCount(UUID fundId, long investorCount) {
}
