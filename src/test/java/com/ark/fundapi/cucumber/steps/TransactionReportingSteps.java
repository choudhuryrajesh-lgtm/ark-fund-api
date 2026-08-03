package com.ark.fundapi.cucumber.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.ark.fundapi.cucumber.steps.HttpSupport.jsonEntity;
import static org.assertj.core.api.Assertions.assertThat;

public class TransactionReportingSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Given("that client has a fund named {string} inception {string}")
    public void thatClientHasAFund(String fundName, String inceptionDate) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/clients/" + context.clientId + "/funds",
                jsonEntity("""
                        {"name":"%s","inceptionDate":"%s"}
                        """.formatted(fundName, inceptionDate)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        context.fundIdsByName().put(fundName, objectMapper.readTree(response.getBody()).get("id").asText());
    }

    @Given("that client has an investor named {string} with email {string}")
    public void thatClientHasAnInvestor(String investorName, String email) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/clients/" + context.clientId + "/investors",
                jsonEntity("""
                        {"name":"%s","email":"%s"}
                        """.formatted(investorName, email)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        context.investorIdsByName().put(investorName, objectMapper.readTree(response.getBody()).get("id").asText());
    }

    @When("{string} contributes {string} to {string} on {string}")
    public void contributes(String investorName, String amount, String fundName, String date) {
        postTransaction(investorName, fundName, "CONTRIBUTION", amount, date);
    }

    @When("{string} receives {string} interest income from {string} on {string}")
    public void receivesInterestIncome(String investorName, String amount, String fundName, String date) {
        postTransaction(investorName, fundName, "INTEREST_INCOME", amount, date);
    }

    private void postTransaction(String investorName, String fundName, String type, String amount, String date) {
        String payload = """
                {"fundId":"%s","investorId":"%s","type":"%s","amount":%s,"transactionDate":"%s"}
                """.formatted(context.fundIdsByName().get(fundName), context.investorIdsByName().get(investorName),
                type, amount, date);
        context.lastResponse = restTemplate.postForEntity(
                "/api/v1/clients/" + context.clientId + "/transactions",
                jsonEntity(payload), String.class);
    }

    @Then("the fund report for {string} shows total credits {string}")
    public void theFundReportShowsTotalCredits(String fundName, String expected) throws Exception {
        assertDecimal(fetchFundReport(fundName).at("/totals/totalCredits"), expected);
    }

    @Then("the fund report for {string} shows net balance {string}")
    public void theFundReportShowsNetBalance(String fundName, String expected) throws Exception {
        assertDecimal(fetchFundReport(fundName).at("/totals/netBalance"), expected);
    }

    @Then("the fund report for {string} lists {int} investors")
    public void theFundReportListsInvestors(String fundName, int count) throws Exception {
        assertThat(fetchFundReport(fundName).get("investorCount").asInt()).isEqualTo(count);
    }

    private JsonNode fetchFundReport(String fundName) throws Exception {
        String fundId = context.fundIdsByName().get(fundName);
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/clients/" + context.clientId + "/reports/funds/" + fundId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.isMissingNode()).as("expected node to be present").isFalse();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }
}
