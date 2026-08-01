package com.ark.fundapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ArkFundApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArkFundApiApplication.class, args);
    }
}
