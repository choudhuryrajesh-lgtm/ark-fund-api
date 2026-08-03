Feature: Client lifecycle
  Clients are the top-level tenant in this API. Every fund, investor and
  transaction is scoped to one.

  Scenario: Creating a client
    When I create a client named "Component Test Client" with email "component-client@example.com"
    Then the client is created successfully
    And I can fetch that client by id

  Scenario: Rejecting a duplicate email
    Given a client named "Duplicate Email Client" with email "duplicate-component@example.com" exists
    When I create a client named "Another Client" with email "duplicate-component@example.com"
    Then the request is rejected as a business rule violation

  Scenario: Deleting a client with no funds or investors
    Given a client named "Deletable Client" with email "deletable-component@example.com" exists
    When I delete that client
    Then the client no longer exists
