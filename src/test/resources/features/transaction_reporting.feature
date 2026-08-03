Feature: Transaction reporting
  End-to-end scenarios spanning client, fund, investor and transaction
  together, verified against a real PostgreSQL instance (not H2's
  compatibility mode) — the fund report's aggregation SQL is exactly the
  kind of query where a Postgres-specific behavior could differ from H2.

  Scenario: A fund report totals credits and debits across investors
    Given a client named "Component Report Client" with email "component-report@example.com" exists
    And that client has a fund named "Component Growth Fund" inception "2024-01-01"
    And that client has an investor named "Component Investor A" with email "component-a@example.com"
    And that client has an investor named "Component Investor B" with email "component-b@example.com"
    When "Component Investor A" contributes "100000.00" to "Component Growth Fund" on "2024-02-01"
    And "Component Investor A" receives "5000.00" interest income from "Component Growth Fund" on "2024-06-30"
    And "Component Investor B" contributes "50000.00" to "Component Growth Fund" on "2024-03-01"
    Then the fund report for "Component Growth Fund" shows total credits "155000.00"
    And the fund report for "Component Growth Fund" shows net balance "155000.00"
    And the fund report for "Component Growth Fund" lists 2 investors

  Scenario: A transaction cannot be posted before its fund existed
    Given a client named "Inception Component Client" with email "component-inception@example.com" exists
    And that client has a fund named "Late Component Fund" inception "2024-06-01"
    And that client has an investor named "Early Component Investor" with email "component-early@example.com"
    When "Early Component Investor" contributes "1000.00" to "Late Component Fund" on "2024-01-01"
    Then the request is rejected as a business rule violation
