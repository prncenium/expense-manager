package com.expensemanager.assistant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RupeesTest {

    private static String f(String amount) {
        return Rupees.format(new BigDecimal(amount));
    }

    @Test
    void usesIndianGrouping() {
        assertThat(f("0")).isEqualTo("₹0");
        assertThat(f("999")).isEqualTo("₹999");
        assertThat(f("1000")).isEqualTo("₹1,000");
        assertThat(f("100000")).isEqualTo("₹1,00,000");
        assertThat(f("124500")).isEqualTo("₹1,24,500");
        assertThat(f("12345678.5")).isEqualTo("₹1,23,45,678.50");
    }

    @Test
    void showsPaiseOnlyWhenPresent() {
        assertThat(f("233.5")).isEqualTo("₹233.50");
        assertThat(f("4200.00")).isEqualTo("₹4,200");
        assertThat(f("0.05")).isEqualTo("₹0.05");
        assertThat(f("-1500")).isEqualTo("-₹1,500");
    }
}
