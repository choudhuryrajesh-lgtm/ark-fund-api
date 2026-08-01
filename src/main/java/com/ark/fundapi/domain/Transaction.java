package com.ark.fundapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An investor's transaction against a fund on a given date.
 *
 * <p>This is the ledger: it is simultaneously the record of money moving and
 * the expression of which investors participate in which funds.
 *
 * <p>{@code amount} is always positive. Direction comes from {@link #type};
 * see {@link TransactionType#applySign(BigDecimal)}.
 */
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    // Denormalised tenant reference — see the note in V1__initial_schema.sql.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    // Foreign key to transaction_types.code rather than an enum: a new
    // transaction type is added by inserting a row there, not by redeploying
    // this service. The FK still guarantees a transaction can never carry a
    // type the business hasn't explicitly classified as credit or debit.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type", referencedColumnName = "code", nullable = false)
    private TransactionType type;

    // BigDecimal, never double — binary floating point cannot represent decimal
    // currency values exactly, and rounding drift is unacceptable in a ledger.
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(Client client, Fund fund, Investor investor, TransactionType type,
                       BigDecimal amount, LocalDate transactionDate, String notes) {
        this.client = client;
        this.fund = fund;
        this.investor = investor;
        this.type = type;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.notes = notes;
    }

    /** The amount's effect on the fund balance: positive for credits, negative for debits. */
    public BigDecimal getSignedAmount() {
        return type.applySign(amount);
    }

    public Client getClient() {
        return client;
    }

    public Fund getFund() {
        return fund;
    }

    public void setFund(Fund fund) {
        this.fund = fund;
    }

    public Investor getInvestor() {
        return investor;
    }

    public void setInvestor(Investor investor) {
        this.investor = investor;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
