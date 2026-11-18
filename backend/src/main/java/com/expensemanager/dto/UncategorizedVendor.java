package com.expensemanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A vendor whose expenses sit in Other with no nickname (SPEC A.5). keyword = suggested rule keyword. */
public record UncategorizedVendor(String vendor, String keyword, long count, BigDecimal total, LocalDate lastDate) {
}
