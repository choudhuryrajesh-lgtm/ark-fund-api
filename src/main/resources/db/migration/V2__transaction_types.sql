-- Transaction types move from a fixed application enum to a governed reference
-- table. The business rule that determines credit/debit direction moves with
-- it: a new type is now added by inserting a row here (an operational, data
-- change) rather than by shipping a code change. transactions.type is
-- foreign-keyed to this table so a transaction can never carry a type the
-- business hasn't explicitly defined and classified.

CREATE TABLE transaction_types (
    code         VARCHAR(40)   PRIMARY KEY,
    direction    VARCHAR(10)   NOT NULL,
    description  VARCHAR(200)  NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT ck_transaction_types_direction CHECK (direction IN ('CREDIT', 'DEBIT'))
);

INSERT INTO transaction_types (code, direction, description, active, created_at, updated_at) VALUES
    ('CONTRIBUTION',    'CREDIT', 'Capital contributed by an investor into a fund',        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INTEREST_INCOME', 'CREDIT', 'Interest income earned by a fund',                       TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DISTRIBUTION',    'DEBIT',  'Capital or income distributed from a fund to an investor', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('GENERAL_EXPENSE', 'DEBIT',  'A general expense charged to a fund',                    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MANAGEMENT_FEE',  'DEBIT',  'A management fee charged to a fund',                     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Runs before the demo seed data (V3), so the FK exists from the start and
-- every future insert — seeded or real — is governed by it from day one.
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_type FOREIGN KEY (type) REFERENCES transaction_types (code);