package com.ark.fundapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI arkOpenApi() {
        return new OpenAPI().info(new Info()
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
