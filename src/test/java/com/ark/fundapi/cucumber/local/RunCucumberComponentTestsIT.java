package com.ark.fundapi.cucumber.local;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Entry point for the component test suite — run via
 * {@code mvn verify -Pcomponent-tests} (see pom.xml). The {@code IT} suffix
 * is deliberate: Failsafe's default include pattern picks it up, Surefire's
 * default pattern doesn't, so a plain {@code mvn test}/{@code mvn verify}
 * never touches this (and never needs Docker for it).
 *
 * <p>Glue lists two leaf packages explicitly — {@code .steps} (shared with
 * {@link com.ark.fundapi.cucumber.smoke.RunCucumberSmokeTestsIT}) and
 * {@code .local} (this runner's own context config, which boots the app
 * itself). Neither is a subpackage of the other, so Cucumber never discovers
 * both this class's {@code CucumberSpringConfiguration} and the smoke
 * runner's different one in the same run — exactly the "two
 * @CucumberContextConfiguration classes found" conflict that would happen
 * if these lived together in one package.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.ark.fundapi.cucumber.steps,com.ark.fundapi.cucumber.local")
public class RunCucumberComponentTestsIT {
}
