package com.ark.fundapi.cucumber.smoke;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Post-deploy smoke test entry point — run via
 * {@code mvn verify -Psmoke-tests -DSMOKE_BASE_URL=https://...} (see
 * pom.xml), or via {@code SMOKE_BASE_URL} env var (what
 * .github/workflows/ci-cd.yml's deploy-demo job uses). Same feature files
 * as {@code com.ark.fundapi.cucumber.local.RunCucumberComponentTestsIT},
 * different context wiring — see {@link SmokeCucumberConfiguration}'s class
 * comment for why these need to be two separate runners at all.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.ark.fundapi.cucumber.steps,com.ark.fundapi.cucumber.smoke")
public class RunCucumberSmokeTestsIT {
}
