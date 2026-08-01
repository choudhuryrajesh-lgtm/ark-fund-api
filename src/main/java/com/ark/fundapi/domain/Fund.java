package com.ark.fundapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * An investment fund belonging to a client.
 *
 * <p>The relationship between funds and investors is deliberately not modelled
 * as a JPA {@code @ManyToMany}. Investors relate to funds <em>through
 * transactions</em> — the association carries a date, an amount and a type, and
 * a plain join table could not express any of that. Deriving participation from
 * the transaction ledger keeps a single source of truth: an investor is in a
 * fund because there is money behind it, not because a row exists somewhere.
 */
@Entity
@Table(name = "funds")
public class Fund extends BaseEntity {

    // LAZY throughout: reports read thousands of transactions, and eagerly
    // hydrating the owning client on each one is a needless N+1.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "inception_date", nullable = false)
    private LocalDate inceptionDate;

    protected Fund() {
        // required by JPA
    }

    public Fund(Client client, String name, String description, LocalDate inceptionDate) {
        this.client = client;
        this.name = name;
        this.description = description;
        this.inceptionDate = inceptionDate;
    }

    public Client getClient() {
        return client;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getInceptionDate() {
        return inceptionDate;
    }

    public void setInceptionDate(LocalDate inceptionDate) {
        this.inceptionDate = inceptionDate;
    }
}
