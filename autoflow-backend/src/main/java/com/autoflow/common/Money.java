package com.autoflow.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable Monetary Value Object.
 * Strictly prevents floating-point inaccuracies using BigDecimal with 2-decimal scale (HALF_EVEN banker's rounding).
 */
@Getter
@Embeddable
@EqualsAndHashCode
public final class Money implements Comparable<Money>, Serializable {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final String DEFAULT_CURRENCY = "INR";

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private final BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private final String currency;

    protected Money() {
        this.amount = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        this.currency = DEFAULT_CURRENCY;
    }

    @JsonCreator
    public Money(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        this.amount = amount.setScale(SCALE, ROUNDING);
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : DEFAULT_CURRENCY;
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money inr(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money inr(long rupees) {
        return new Money(BigDecimal.valueOf(rupees), DEFAULT_CURRENCY);
    }

    public static Money inr(double amount) {
        return new Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money zeroInr() {
        return zero(DEFAULT_CURRENCY);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Factor cannot be null");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money multiply(long factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public Money calculatePercentage(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "Percentage cannot be null");
        BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 4, ROUNDING);
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "Money comparison target cannot be null");
        if (!this.currency.equalsIgnoreCase(other.currency)) {
            throw new IllegalArgumentException("Cannot operate across differing currencies: "
                    + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
