package com.autoflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money Financial Value Object Unit Tests")
class MoneyTest {

    @Test
    @DisplayName("Should initialize with exact 2-decimal scale")
    void testScaleAndCurrency() {
        Money m = Money.inr(new BigDecimal("499"));
        assertThat(m.getAmount()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat(m.getAmount().scale()).isEqualTo(2);
        assertThat(m.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("Should perform exact addition without floating-point errors")
    void testAddition() {
        Money a = Money.inr(new BigDecimal("0.10"));
        Money b = Money.inr(new BigDecimal("0.20"));
        Money result = a.add(b);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(result.getAmount().toString()).isEqualTo("0.30");
    }

    @Test
    @DisplayName("Should perform exact subtraction")
    void testSubtraction() {
        Money gross = Money.inr(new BigDecimal("1000.00"));
        Money discount = Money.inr(new BigDecimal("250.50"));
        Money net = gross.subtract(discount);

        assertThat(net.getAmount()).isEqualByComparingTo(new BigDecimal("749.50"));
    }

    @Test
    @DisplayName("Should accurately calculate commission and discounts using percentage")
    void testPercentageCalculation() {
        // Plan: 499, Discount: 20% -> 99.80 discount -> Customer pays 399.20
        Money planPrice = Money.inr(new BigDecimal("499.00"));
        Money discount = planPrice.calculatePercentage(new BigDecimal("20.00"));
        Money customerPays = planPrice.subtract(discount);

        assertThat(discount.getAmount()).isEqualByComparingTo(new BigDecimal("99.80"));
        assertThat(customerPays.getAmount()).isEqualByComparingTo(new BigDecimal("399.20"));

        // Commission: 30% of 399.20 -> 119.76
        Money commission = customerPays.calculatePercentage(new BigDecimal("30.00"));
        assertThat(commission.getAmount()).isEqualByComparingTo(new BigDecimal("119.76"));
    }

    @Test
    @DisplayName("Should reject cross-currency arithmetic")
    void testCurrencyMismatchThrows() {
        Money inr = Money.of(new BigDecimal("100.00"), "INR");
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThatThrownBy(() -> inr.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot operate across differing currencies");
    }

    @Test
    @DisplayName("Should evaluate comparison operators correctly")
    void testComparisons() {
        Money low = Money.inr(new BigDecimal("100.00"));
        Money high = Money.inr(new BigDecimal("500.00"));
        Money equal = Money.inr(new BigDecimal("100.00"));

        assertThat(high.isGreaterThan(low)).isTrue();
        assertThat(low.isLessThan(high)).isTrue();
        assertThat(low.isGreaterThanOrEqual(equal)).isTrue();
        assertThat(low.compareTo(equal)).isZero();
    }
}
