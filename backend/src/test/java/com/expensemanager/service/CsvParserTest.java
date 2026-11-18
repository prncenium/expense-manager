package com.expensemanager.service;

import com.expensemanager.dto.UploadResult.RowError;
import com.expensemanager.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(TODAY.atTime(12, 0), ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));
    private final CsvParser parser = new CsvParser(new ExpenseValidator(clock));

    private CsvParser.ParsedCsv parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsBothDateFormatsWithDashOrSlash() {
        var result = parse("""
                date,amount,vendor,description
                2026-11-01,100,Swiggy,a
                05-11-2026,100,Zomato,b
                2026/11/02,100,Uber,c
                06/11/2026,100,Ola,d
                """);
        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).extracting(CsvParser.CsvRow::date).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6));
    }

    @Test
    void stripsRupeeSignAndCommas() {
        var result = parse("""
                date,amount,vendor
                2026-11-01,"₹1,250.50",Flipkart
                2026-11-01,"12,34,567",Myntra
                """);
        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).extracting(CsvParser.CsvRow::amount)
                .containsExactly(new BigDecimal("1250.50"), new BigDecimal("1234567"));
    }

    @Test
    void headerIsCaseInsensitiveInAnyOrderAndDescriptionOptional() {
        var result = parse("""
                Vendor,DATE,Amount
                Swiggy,2026-11-01,99
                """);
        assertThat(result.valid()).singleElement().satisfies(r -> {
            assertThat(r.vendor()).isEqualTo("Swiggy");
            assertThat(r.description()).isNull();
        });
    }

    @Test
    void missingHeaderIsRejected() {
        assertThatThrownBy(() -> parse("2026-11-01,100,Swiggy,lunch\n"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("header");
    }

    @Test
    void headerWithoutRequiredColumnIsRejected() {
        assertThatThrownBy(() -> parse("date,vendor\n2026-11-01,Swiggy\n"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("header");
    }

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> parse("\n\n")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void badAmountIsReportedAndOtherRowsKept() {
        var result = parse("""
                date,amount,vendor
                2026-11-01,abc,Swiggy
                2026-11-01,0,Swiggy
                2026-11-01,-5,Swiggy
                2026-11-01,100,Swiggy
                """);
        assertThat(result.valid()).hasSize(1);
        assertThat(result.errors()).containsExactly(
                new RowError(2, "amount must be a number"),
                new RowError(3, "amount must be > 0"),
                new RowError(4, "amount must be > 0"));
    }

    @Test
    void emptyVendorIsReported() {
        var result = parse("""
                date,amount,vendor
                2026-11-01,100,
                2026-11-01,100,"   "
                """);
        assertThat(result.errors()).extracting(RowError::reason).containsOnly("vendor is required");
    }

    @Test
    void blankLinesAreIgnoredAndRowNumbersAreFileLines() {
        var result = parse("date,amount,vendor\n\n2026-11-01,100,Swiggy\n\n,,\n2026-11-01,x,Zomato\n\n");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.valid()).extracting(CsvParser.CsvRow::row).containsExactly(3);
        assertThat(result.errors()).containsExactly(new RowError(6, "amount must be a number"));
    }

    @Test
    void futureDateIsRejectedButTodayIsFine() {
        var result = parse("""
                date,amount,vendor
                2026-11-18,100,Swiggy
                2026-11-19,100,Swiggy
                """);
        assertThat(result.valid()).hasSize(1);
        assertThat(result.errors()).containsExactly(new RowError(3, "date cannot be in the future"));
    }

    @Test
    void invalidDateIsReported() {
        var result = parse("""
                date,amount,vendor
                31-02-2026,100,Swiggy
                yesterday,100,Swiggy
                """);
        assertThat(result.errors()).extracting(RowError::reason)
                .containsOnly("date must be YYYY-MM-DD or DD-MM-YYYY");
    }

    @Test
    void amountOverOneCroreIsRejected() {
        var result = parse("""
                date,amount,vendor
                2026-11-01,10000000,Big Purchase
                2026-11-01,10000000.01,Too Big
                """);
        assertThat(result.valid()).hasSize(1);
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(3);
            assertThat(e.reason()).startsWith("amount must be ≤");
        });
    }

    @Test
    void severalProblemsInOneRowAreJoined() {
        var result = parse("""
                date,amount,vendor
                ,-1,
                """);
        assertThat(result.errors()).singleElement().extracting(RowError::reason)
                .isEqualTo("date is required; amount must be > 0; vendor is required");
    }

    @Test
    void moreThan5000RowsIsRejected() {
        StringBuilder csv = new StringBuilder("date,amount,vendor\n");
        for (int i = 0; i < CsvParser.MAX_ROWS + 1; i++) {
            csv.append("2026-11-01,").append(i + 1).append(",Vendor\n");
        }
        assertThatThrownBy(() -> parse(csv.toString()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Too many rows");
    }

    @Test
    void exactly5000RowsIsAllowed() {
        StringBuilder csv = new StringBuilder("date,amount,vendor\n");
        for (int i = 0; i < CsvParser.MAX_ROWS; i++) {
            csv.append("2026-11-01,").append(i + 1).append(",Vendor\n");
        }
        assertThat(parse(csv.toString()).valid()).hasSize(CsvParser.MAX_ROWS);
    }
}
