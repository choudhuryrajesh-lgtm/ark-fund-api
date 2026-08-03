package com.ark.fundapi.cucumber.smoke;

import com.ark.fundapi.cucumber.steps.TestContext;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Runs the exact same feature files and step definitions as
 * {@code com.ark.fundapi.cucumber.local}, but against an already-running,
 * already-deployed instance — {@code demo} in AWS, typically — instead of
 * booting the app itself. That's the whole point: this treats the deployed
 * service as a real black box (HTTP only, never touching its database
 * directly), which is what an actual post-deploy smoke/acceptance gate
 * needs to verify, as opposed to the {@code local} runner's job of proving
 * the code itself is correct against a real live dependency.
 *
 * <p>No {@code @SpringBootTest} here deliberately — that would boot a
 * second copy of the application locally, which is the opposite of what a
 * smoke test against a real deployment should do. Just enough Spring
 * context to satisfy cucumber-spring's DI (a {@link TestRestTemplate}
 * pointed at the target URL, and a scenario-scoped {@link TestContext}),
 * nothing else.
 *
 * <p>Deliberately carries no {@code @Configuration}/{@code @TestConfiguration}
 * stereotype — both are meta-annotated with {@code @Component}, which
 * caused two separate real failures: (1) any other test's plain
 * {@code @SpringBootTest} (FundApiIntegrationTest, for one) component-scans
 * straight through this package and collided with {@code TestContext}'s own
 * {@code @Component} registration; (2) Cucumber's own glue-path scanning
 * flags a {@code @Component}-family class sitting inside its glue package
 * ("may lead to duplicate bean definitions"). Spring's test framework
 * processes {@code @Bean} methods correctly on a plain class explicitly
 * passed via {@code @ContextConfiguration(classes = ...)} below — no
 * stereotype annotation needed for that to work.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = SmokeCucumberConfiguration.class)
public class SmokeCucumberConfiguration {

    @Bean
    public TestRestTemplate testRestTemplate() {
        String baseUrl = System.getProperty("smoke.base.url", System.getenv("SMOKE_BASE_URL"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Smoke tests need a target URL — pass -Dsmoke.base.url=https://... "
                            + "or set the SMOKE_BASE_URL environment variable.");
        }
        // Defensive, not just documentation: demo's API Gateway invoke_url
        // ends in a trailing slash (see terraform/README.md's api_url
        // output). Concatenating that with a request path starting in "/"
        // produces a literal double slash — the exact bug that caused a
        // false 500 earlier ("//actuator/health" not matching any route).
        // Stripping it here means the caller doesn't have to remember that.
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return new TestRestTemplate(new RestTemplateBuilder().rootUri(baseUrl));
    }

    @Bean
    @ScenarioScope
    public TestContext testContext() {
        return new TestContext();
    }
}
