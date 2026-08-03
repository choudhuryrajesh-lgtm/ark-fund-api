package com.ark.fundapi.cucumber.local;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wires the Cucumber component suite to a real, running app instance
 * (RANDOM_PORT — actual HTTP layer, not MockMvc) backed by a real
 * PostgreSQL — not H2's compatibility mode used by the fast unit/
 * integration tier (see FundApiIntegrationTest). This is what makes these
 * "component + live dependency" tests rather than another unit test: the
 * whole app, talking to the same database engine production actually runs
 * against.
 *
 * <p>Deliberately points at the same Postgres {@code docker-compose.yml}
 * already runs for local development (localhost:5432/arkdb — see
 * {@code application.yml}'s defaults, unchanged here) rather than a
 * Testcontainers-managed instance. Testcontainers' bundled Docker client had
 * an unresolved compatibility issue with Docker Desktop in this environment;
 * reusing the already-proven docker-compose Postgres sidesteps it entirely
 * while still exercising a genuine, real Postgres. Requires
 * {@code docker compose up -d db} to be running first — see
 * {@code README.md}'s testing section.
 *
 * <p>This class carries no {@code @Test} methods itself — pure context
 * wiring that {@code RunCucumberComponentTestsIT} bootstraps.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("component-test")
public class CucumberSpringConfiguration {
}
