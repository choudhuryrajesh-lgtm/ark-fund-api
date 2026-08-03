package com.ark.fundapi.cucumber.steps;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state for one Cucumber scenario, injected into every step
 * definition class that needs it — cucumber-spring creates a fresh instance
 * per scenario ({@code @ScenarioScope}), so scenarios never leak state into
 * each other even though they share one Spring context/database.
 *
 * <p>Maps use lazy, self-initializing accessors rather than a field
 * initializer or {@code @PostConstruct} — both were observed, in practice,
 * to not reliably run before first use under cucumber-spring's
 * scenario-scoped bean creation. Lazy init at the access point is correct
 * regardless of how/when the underlying instance actually gets constructed.
 *
 * <p>Public specifically so {@code com.ark.fundapi.cucumber.smoke}'s context
 * config (a different package — see the class-level note on
 * {@code RunCucumberSmokeTestsIT}) can construct it directly as a
 * {@code @Bean} without needing component-scanning to reach across packages.
 */
@Component
@ScenarioScope
public class TestContext {

    String clientId;
    private Map<String, String> fundIdsByName;
    private Map<String, String> investorIdsByName;
    ResponseEntity<String> lastResponse;

    Map<String, String> fundIdsByName() {
        if (fundIdsByName == null) {
            fundIdsByName = new HashMap<>();
        }
        return fundIdsByName;
    }

    Map<String, String> investorIdsByName() {
        if (investorIdsByName == null) {
            investorIdsByName = new HashMap<>();
        }
        return investorIdsByName;
    }
}
