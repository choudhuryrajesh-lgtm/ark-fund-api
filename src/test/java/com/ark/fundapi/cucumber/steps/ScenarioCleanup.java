package com.ark.fundapi.cucumber.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/**
 * Deletes everything a scenario created, once that scenario finishes.
 *
 * <p>These suites run against a long-lived database, not a disposable one:
 * the component suite points at the same {@code docker compose} Postgres
 * volume used for local development, and the smoke suite points at a
 * deployed environment. Without teardown, every run permanently added its
 * clients, funds, investors and transactions — so the demo UI's client
 * picker slowly filled up with "Component Test Client" entries, and the
 * portfolio report for a real client sat among dozens of test tenants.
 *
 * <p>Lives in the {@code steps} package deliberately: both
 * {@link com.ark.fundapi.cucumber.local.RunCucumberComponentTestsIT} and
 * {@link com.ark.fundapi.cucumber.smoke.RunCucumberSmokeTestsIT} glue this
 * package, so one hook covers both rather than each runner needing its own.
 */
public class ScenarioCleanup {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCleanup.class);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Runs after every scenario, passed or failed. Deliberately not
     * conditional on success: a scenario that fails half way through has
     * still created rows, and those are exactly the ones that would
     * otherwise accumulate.
     */
    @After
    public void deleteWhatThisScenarioCreated() {
        String clientId = context.clientId;
        if (clientId == null) {
            return;
        }

        // Order matters. Transactions reference funds and investors, and all
        // three reference the client; the schema uses plain foreign keys with
        // no ON DELETE CASCADE (V1__initial_schema.sql), and the API refuses
        // to delete a fund or investor that still has transactions. So the
        // ledger goes first and the client goes last.
        String base = "/api/v1/clients/" + clientId;
        deleteEach(base + "/transactions", "?size=500");
        deleteEach(base + "/funds", "?size=500");
        deleteEach(base + "/investors", "?size=500");
        restTemplate.delete(base);
    }

    /**
     * Lists a collection straight from the API and deletes each row, rather
     * than replaying IDs recorded in {@link TestContext} — that way anything
     * a scenario created through a path the context doesn't track still gets
     * cleaned up.
     */
    private void deleteEach(String collectionPath, String pageQuery) {
        ResponseEntity<String> response =
                restTemplate.getForEntity(collectionPath + pageQuery, String.class);

        // A 404 here is normal, not a problem: the "deleting a client"
        // scenario removes its own client, so by teardown there is nothing
        // left to enumerate.
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return;
        }

        try {
            for (JsonNode row : objectMapper.readTree(response.getBody()).path("content")) {
                restTemplate.delete(collectionPath + "/" + row.get("id").asText());
            }
        } catch (Exception e) {
            // Teardown must never turn a passing scenario red. Leftover rows
            // are untidy; a spurious build failure is worse.
            log.warn("Could not fully clean up {} — leftover test data may remain: {}",
                    collectionPath, e.toString());
        }
    }
}
