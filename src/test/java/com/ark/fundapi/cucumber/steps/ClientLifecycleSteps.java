package com.ark.fundapi.cucumber.steps;

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

public class ClientLifecycleSteps {

    // Component tests run against docker-compose's persistent Postgres
    // volume, not a disposable per-run container (see
    // CucumberSpringConfiguration) — without this, re-running the suite a
    // second time would collide with client emails the first run already
    // created. Computed once per JVM run, so both steps in the "duplicate
    // email" scenario still produce the identical actual email as each
    // other (correctly triggering the duplicate check), while differing
    // from every other run's emails.
    private static final String RUN_SUFFIX = Long.toString(System.currentTimeMillis());

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @When("I create a client named {string} with email {string}")
    public void iCreateAClient(String name, String email) throws Exception {
        context.lastResponse = restTemplate.postForEntity("/api/v1/clients",
                jsonEntity("""
                        {"name":"%s","email":"%s"}
                        """.formatted(name, uniquify(email))), String.class);
        if (context.lastResponse.getStatusCode() == HttpStatus.CREATED) {
            context.clientId = objectMapper.readTree(context.lastResponse.getBody()).get("id").asText();
        }
    }

    private static String uniquify(String email) {
        int at = email.indexOf('@');
        return email.substring(0, at) + "+" + RUN_SUFFIX + email.substring(at);
    }

    @Given("a client named {string} with email {string} exists")
    public void aClientExists(String name, String email) throws Exception {
        iCreateAClient(name, email);
        assertThat(context.lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Then("the client is created successfully")
    public void theClientIsCreatedSuccessfully() {
        assertThat(context.lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Then("I can fetch that client by id")
    public void iCanFetchThatClient() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/clients/" + context.clientId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Then("the request is rejected as a business rule violation")
    public void theRequestIsRejectedAsABusinessRuleViolation() {
        assertThat(context.lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @When("I delete that client")
    public void iDeleteThatClient() {
        restTemplate.delete("/api/v1/clients/" + context.clientId);
    }

    @Then("the client no longer exists")
    public void theClientNoLongerExists() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/clients/" + context.clientId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
