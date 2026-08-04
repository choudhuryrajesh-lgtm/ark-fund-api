package com.ark.fundapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI arkOpenApi() {
        return new OpenAPI()
                // A relative server URL, rather than letting springdoc derive an
                // absolute one from the incoming request.
                //
                // TLS terminates at API Gateway and the internal ALB speaks plain
                // HTTP to the tasks, so a derived URL comes out as
                // `http://<host>` — which Swagger UI, loaded over https, then
                // refuses to call as mixed content ("Failed to fetch"). Forwarded
                // headers don't rescue it either: the ALB sets X-Forwarded-Proto
                // from its own listener, which is HTTP:80 in the demo environment.
                //
                // "/" sidesteps the whole question. Swagger UI is served from the
                // same origin as the API, so a relative URL resolves against
                // whatever scheme and host the page itself was loaded with —
                // correct on localhost, behind API Gateway, and behind CloudFront
                // alike, with nothing to configure per environment.
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                .title("Ark Fund API")
                .version("1.0.0")
                .description("""
                        Investment management and reporting API.

                        Clients (tenants) own funds and investors. Investors interact with funds
                        through transactions; a transaction's type determines whether it credits
                        or debits the fund:

                        - CONTRIBUTION (credit)
                        - INTEREST_INCOME (credit)
                        - DISTRIBUTION (debit)
                        - GENERAL_EXPENSE (debit)
                        - MANAGEMENT_FEE (debit)
                        """));
    }
}
