package com.ark.fundapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A transaction type the business supports, together with its effect on a
 * fund's balance.
 *
 * <p>Backed by the {@code transaction_types} table rather than a fixed enum,
 * so a new type (a new fee category, a new kind of distribution) is added by
 * inserting a row rather than shipping a code change. {@link Transaction#getType()}
 * carries a foreign key into this table, so a transaction can never reference
 * a type the business hasn't explicitly defined and classified as a credit or
 * a debit.
 */
@Entity
@Table(name = "transaction_types")
@EntityListeners(AuditingEntityListener.class)
public class TransactionType {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionType() {
        // required by JPA
    }

    public TransactionType(String code, TransactionDirection direction, String description) {
        this.code = code;
        this.direction = direction;
        this.description = description;
    }

    public boolean isCredit() {
        return direction == TransactionDirection.CREDIT;
    }

    public boolean isDebit() {
        return direction == TransactionDirection.DEBIT;
    }

    /**
     * Converts a stored (always positive) amount into its signed effect on a
     * fund balance: credits add, debits subtract.
     */
    public BigDecimal applySign(BigDecimal amount) {
        return isCredit() ? amount : amount.negate();
    }

    public String getCode() {
        return code;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Equality by code — this is a natural-key reference entity, not an identity-generated one. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionType that)) {
            return false;
        }
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}