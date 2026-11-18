package com.expensemanager.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    @Test
    void formulaCellsAreEscaped() {
        assertThat(ExportService.safe("=HYPERLINK(\"x\")")).isEqualTo("'=HYPERLINK(\"x\")");
        assertThat(ExportService.safe("+91 call")).isEqualTo("'+91 call");
        assertThat(ExportService.safe("-5")).isEqualTo("'-5");
        assertThat(ExportService.safe("@sum")).isEqualTo("'@sum");
        assertThat(ExportService.safe("Swiggy")).isEqualTo("Swiggy");
        assertThat(ExportService.safe(null)).isEqualTo("");
    }

    @Test
    void changePctRoundsAndIsNullWithoutPrevious() {
        assertThat(DashboardService.changePct(new java.math.BigDecimal("118"), new java.math.BigDecimal("100")))
                .isEqualByComparingTo("18.0");
        assertThat(DashboardService.changePct(new java.math.BigDecimal("0"), new java.math.BigDecimal("300")))
                .isEqualByComparingTo("-100.0");
        assertThat(DashboardService.changePct(new java.math.BigDecimal("50"), java.math.BigDecimal.ZERO)).isNull();
    }
}
