package com.ark.fundapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the HTTP layer.
 *
 * <p>The focus is the reporting arithmetic and the tenant boundary — the two
 * places where a defect would be both silent and financially meaningful.
 * Numbers are compared with {@code BigDecimal.compareTo} rather than JSON-path
 * equality so a scale difference ({@code 100.0} vs {@code 100.00}) cannot make
 * a passing test look like a failing one, or vice versa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FundApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Reporting arithmetic
    // ------------------------------------------------------------------

    @Test
    void fundReportAggregatesCreditsAndDebitsAcrossInvestors() throws Exception {
        String clientId = createClient("Aggregate Test Client", "aggregate@example.com");
        String fundId = createFund(clientId, "Growth Fund", "2024-01-01");
        String investorA = createInvestor(clientId, "Investor A", "a@example.com");
        String investorB = createInvestor(clientId, "Investor B", "b@example.com");

        // Investor A: 100,000 in + 5,000 interest - 2,000 fee  = 103,000
        createTransaction(clientId, fundId, investorA, "CONTRIBUTION", "100000.00", "2024-02-01");
        createTransaction(clientId, fundId, investorA, "INTEREST_INCOME", "5000.00", "2024-06-30");
        createTransaction(clientId, fundId, investorA, "MANAGEMENT_FEE", "2000.00", "2024-07-01");

        // Investor B: 50,000 in - 10,000 distribution           =  40,000
        createTransaction(clientId, fundId, investorB, "CONTRIBUTION", "50000.00", "2024-03-01");
        createTransaction(clientId, fundId, investorB, "DISTRIBUTION", "10000.00", "2024-09-15");

        JsonNode report = getJson("/api/v1/clients/%s/reports/funds/%s".formatted(clientId, fundId));

        // Fund level: credits 155,000, debits 12,000, net 143,000
        assertDecimal(report.at("/totals/totalCredits"), "155000.00");
        assertDecimal(report.at("/totals/totalDebits"), "12000.00");
        assertDecimal(report.at("/totals/netBalance"), "143000.00");

        assertDecimal(report.at("/totals/byType/CONTRIBUTION"), "150000.00");
        assertDecimal(report.at("/totals/byType/INTEREST_INCOME"), "5000.00");
        assertDecimal(report.at("/totals/byType/MANAGEMENT_FEE"), "2000.00");
        assertDecimal(report.at("/totals/byType/DISTRIBUTION"), "10000.00");
        // A type with no activity is still present, at zero.
        assertDecimal(report.at("/totals/byType/GENERAL_EXPENSE"), "0.00");

        assertThat(report.get("investorCount").asInt()).isEqualTo(2);

        // Per-investor positions must sum back to the fund balance.
        JsonNode positions = report.get("investorPositions");
        assertThat(positions).hasSize(2);
        BigDecimal positionSum = BigDecimal.ZERO;
        for (JsonNode position : positions) {
            positionSum = positionSum.add(position.at("/totals/netBalance").decimalValue());
        }
        assertThat(positionSum).isEqualByComparingTo("143000.00");
    }

    @Test
    void investorReportSpansEveryFundTheyParticipateIn() throws Exception {
        String clientId = createClient("Multi Fund Client", "multifund@example.com");
        String growthFund = createFund(clientId, "Growth Fund", "2024-01-01");
        String incomeFund = createFund(clientId, "Income Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Diversified Investor", "diversified@example.com");

        createTransaction(clientId, growthFund, investorId, "CONTRIBUTION", "200000.00", "2024-02-01");
        createTransaction(clientId, incomeFund, investorId, "CONTRIBUTION", "75000.00", "2024-03-01");
        createTransaction(clientId, incomeFund, investorId, "DISTRIBUTION", "5000.00", "2024-08-01");

        JsonNode report = getJson("/api/v1/clients/%s/reports/investors/%s".formatted(clientId, investorId));

        assertThat(report.get("fundCount").asInt()).isEqualTo(2);
        assertDecimal(report.at("/totals/totalCredits"), "275000.00");
        assertDecimal(report.at("/totals/totalDebits"), "5000.00");
        assertDecimal(report.at("/totals/netBalance"), "270000.00");
        assertThat(report.get("fundPositions")).hasSize(2);
    }

    @Test
    void asOfDateExcludesLaterTransactions() throws Exception {
        String clientId = createClient("As Of Client", "asof@example.com");
        String fundId = createFund(clientId, "Timed Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Timed Investor", "timed@example.com");

        createTransaction(clientId, fundId, investorId, "CONTRIBUTION", "100000.00", "2024-02-01");
        createTransaction(clientId, fundId, investorId, "CONTRIBUTION", "50000.00", "2024-08-01");

        JsonNode midYear = getJson(
                "/api/v1/clients/%s/reports/funds/%s?asOfDate=2024-06-30".formatted(clientId, fundId));
        assertDecimal(midYear.at("/totals/netBalance"), "100000.00");

        JsonNode allTime = getJson("/api/v1/clients/%s/reports/funds/%s".formatted(clientId, fundId));
        assertDecimal(allTime.at("/totals/netBalance"), "150000.00");
    }

    @Test
    void portfolioReportIncludesFundsWithNoTransactions() throws Exception {
        String clientId = createClient("Portfolio Client", "portfolio@example.com");
        String activeFund = createFund(clientId, "Active Fund", "2024-01-01");
        createFund(clientId, "Dormant Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Portfolio Investor", "pinv@example.com");

        createTransaction(clientId, activeFund, investorId, "CONTRIBUTION", "10000.00", "2024-02-01");

        JsonNode report = getJson("/api/v1/clients/%s/reports/portfolio".formatted(clientId));

        // The dormant fund must still be listed — a fund disappearing from a
        // portfolio view because it has no activity would read as data loss.
        assertThat(report.get("fundCount").asInt()).isEqualTo(2);
        assertDecimal(report.at("/totals/netBalance"), "10000.00");
    }

    // ------------------------------------------------------------------
    // Tenant isolation and business rules
    // ------------------------------------------------------------------

    @Test
    void transactionCannotLinkAFundAndInvestorFromDifferentClients() throws Exception {
        String clientOne = createClient("Client One", "one@example.com");
        String clientTwo = createClient("Client Two", "two@example.com");

        String fundOfClientOne = createFund(clientOne, "Client One Fund", "2024-01-01");
        String investorOfClientTwo = createInvestor(clientTwo, "Client Two Investor", "ctwo@example.com");

        String payload = """
                {"fundId":"%s","investorId":"%s","type":"CONTRIBUTION",
                 "amount":1000.00,"transactionDate":"2024-02-01"}
                """.formatted(fundOfClientOne, investorOfClientTwo);

        // 404 rather than 400: client one must not be able to learn that an
        // investor with that id exists at all.
        mockMvc.perform(post("/api/v1/clients/{clientId}/transactions", clientOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void readingAnotherClientsFundReturnsNotFound() throws Exception {
        String clientOne = createClient("Owner Client", "owner@example.com");
        String clientTwo = createClient("Other Client", "other@example.com");
        String fundId = createFund(clientOne, "Private Fund", "2024-01-01");

        mockMvc.perform(get("/api/v1/clients/{clientId}/funds/{fundId}", clientTwo, fundId))
                .andExpect(status().isNotFound());
    }

    @Test
    void transactionDatedBeforeFundInceptionIsRejected() throws Exception {
        String clientId = createClient("Inception Client", "inception@example.com");
        String fundId = createFund(clientId, "Late Fund", "2024-06-01");
        String investorId = createInvestor(clientId, "Early Investor", "early@example.com");

        String payload = """
                {"fundId":"%s","investorId":"%s","type":"CONTRIBUTION",
                 "amount":1000.00,"transactionDate":"2024-01-01"}
                """.formatted(fundId, investorId);

        mockMvc.perform(post("/api/v1/clients/{clientId}/transactions", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void nonPositiveAmountFailsValidation() throws Exception {
        String clientId = createClient("Validation Client", "validation@example.com");
        String fundId = createFund(clientId, "Validation Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Validation Investor", "vinv@example.com");

        String payload = """
                {"fundId":"%s","investorId":"%s","type":"CONTRIBUTION",
                 "amount":0.00,"transactionDate":"2024-02-01"}
                """.formatted(fundId, investorId);

        mockMvc.perform(post("/api/v1/clients/{clientId}/transactions", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void fundWithTransactionsCannotBeDeleted() throws Exception {
        String clientId = createClient("Delete Client", "delete@example.com");
        String fundId = createFund(clientId, "Busy Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Delete Investor", "dinv@example.com");
        createTransaction(clientId, fundId, investorId, "CONTRIBUTION", "1000.00", "2024-02-01");

        mockMvc.perform(delete("/api/v1/clients/{clientId}/funds/{fundId}", clientId, fundId))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateFundNameWithinAClientIsRejected() throws Exception {
        String clientId = createClient("Duplicate Client", "duplicate@example.com");
        createFund(clientId, "Flagship Fund", "2024-01-01");

        mockMvc.perform(post("/api/v1/clients/{clientId}/funds", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flagship Fund","inceptionDate":"2024-01-01"}
                                """))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Transaction types (governed reference data, not a fixed enum)
    // ------------------------------------------------------------------

    @Test
    void transactionTypesEndpointListsTheSeededTypesWithCorrectDirection() throws Exception {
        JsonNode types = getJson("/api/v1/transaction-types");

        assertThat(types).hasSize(5);
        assertThat(directionOf(types, "CONTRIBUTION")).isEqualTo("CREDIT");
        assertThat(directionOf(types, "INTEREST_INCOME")).isEqualTo("CREDIT");
        assertThat(directionOf(types, "DISTRIBUTION")).isEqualTo("DEBIT");
        assertThat(directionOf(types, "GENERAL_EXPENSE")).isEqualTo("DEBIT");
        assertThat(directionOf(types, "MANAGEMENT_FEE")).isEqualTo("DEBIT");
    }

    @Test
    void postingATransactionWithAnUnknownTypeCodeIsRejected() throws Exception {
        String clientId = createClient("Unknown Type Client", "unknowntype@example.com");
        String fundId = createFund(clientId, "Unknown Type Fund", "2024-01-01");
        String investorId = createInvestor(clientId, "Unknown Type Investor", "uinv@example.com");

        String payload = """
                {"fundId":"%s","investorId":"%s","type":"CAPITAL_CALL",
                 "amount":1000.00,"transactionDate":"2024-02-01"}
                """.formatted(fundId, investorId);

        // A type code that isn't governed by transaction_types is treated the
        // same as any other reference that doesn't exist: 404, not a silent
        // acceptance of an uncontrolled ledger category.
        mockMvc.perform(post("/api/v1/clients/{clientId}/transactions", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    private static String directionOf(JsonNode types, String code) {
        for (JsonNode type : types) {
            if (type.get("code").asText().equals(code)) {
                return type.get("direction").asText();
            }
        }
        throw new AssertionError("No transaction type with code " + code);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String createClient(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createFund(String clientId, String name, String inceptionDate) throws Exception {
        String body = mockMvc.perform(post("/api/v1/clients/{clientId}/funds", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","inceptionDate":"%s"}
                                """.formatted(name, inceptionDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createInvestor(String clientId, String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/clients/{clientId}/investors", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void createTransaction(String clientId, String fundId, String investorId,
                                   String type, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/v1/clients/{clientId}/transactions", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundId":"%s","investorId":"%s","type":"%s",
                                 "amount":%s,"transactionDate":"%s"}
                                """.formatted(fundId, investorId, type, amount, date)))
                .andExpect(status().isCreated());
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.isMissingNode()).as("expected node to be present").isFalse();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }
}
