package com.expensemanager.service;

import com.expensemanager.dto.UploadResult.RowError;
import com.expensemanager.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Turns an uploaded CSV into validated rows (SPEC §8). Pure: no database access.
 * Whole-file problems (missing/wrong header, too many rows) throw a 400; row problems are
 * collected as {@link RowError}s. Row numbers are file line numbers (header = row 1).
 */
@Component
public class CsvParser {

    public static final int MAX_ROWS = 5000;
    static final String EXPECTED_HEADER = "date,amount,vendor,description (description optional)";

    private static final Set<String> REQUIRED = Set.of("date", "amount", "vendor");
    private static final Set<String> ALLOWED = Set.of("date", "amount", "vendor", "description");
    /** Extra columns of our own export (SPEC D): accepted and ignored, they are recomputed. */
    private static final Set<String> IGNORED = Set.of("category", "label", "high_expense");
    private static final List<String> FIELD_ORDER = List.of("date", "amount", "vendor", "description");

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreEmptyLines(false)   // keep them so record number == line number; skipped below
            .setTrim(true)
            .get();

    private final ExpenseValidator validator;

    public CsvParser(ExpenseValidator validator) {
        this.validator = validator;
    }

    public record CsvRow(int row, LocalDate date, BigDecimal amount, String vendor, String description) {
    }

    /** total = non-blank data rows; valid + errors.size() == total. */
    public record ParsedCsv(int total, List<CsvRow> valid, List<RowError> errors) {
    }

    public ParsedCsv parse(InputStream in) {
        List<CSVRecord> records;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, FORMAT)) {
            records = parser.getRecords();
        } catch (IOException | UncheckedIOException e) {
            throw new BadRequestException("Could not read the CSV file: " + e.getMessage());
        }

        Iterator<CSVRecord> it = records.stream().filter(r -> !isBlank(r)).iterator();
        if (!it.hasNext()) {
            throw new BadRequestException("CSV file is empty. Expected header: " + EXPECTED_HEADER);
        }
        Map<String, Integer> columns = readHeader(it.next());

        List<CSVRecord> dataRows = new ArrayList<>();
        it.forEachRemaining(dataRows::add);
        if (dataRows.size() > MAX_ROWS) {
            throw new BadRequestException("Too many rows: " + dataRows.size() + " (max " + MAX_ROWS + "). Nothing was saved.");
        }

        List<CsvRow> valid = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (CSVRecord rec : dataRows) {
            int row = (int) rec.getRecordNumber();
            String rawDate = value(rec, columns.get("date"));
            String rawAmount = value(rec, columns.get("amount"));
            String vendor = value(rec, columns.get("vendor"));
            String description = value(rec, columns.get("description"));

            Map<String, String> problems = new HashMap<>();
            LocalDate date = null;
            if (rawDate != null) {
                date = parseDate(rawDate);
                if (date == null) {
                    problems.put("date", "must be YYYY-MM-DD or DD-MM-YYYY");
                }
            }
            BigDecimal amount = null;
            if (rawAmount != null) {
                amount = parseAmount(rawAmount);
                if (amount == null) {
                    problems.put("amount", "must be a number");
                }
            }
            validator.validate(date, amount, vendor, description).forEach(problems::putIfAbsent);

            if (problems.isEmpty()) {
                valid.add(new CsvRow(row, date, amount, ExpenseValidator.clean(vendor),
                        ExpenseValidator.clean(description)));
            } else {
                StringJoiner reason = new StringJoiner("; ");
                for (String field : FIELD_ORDER) {
                    if (problems.containsKey(field)) {
                        reason.add(field + " " + problems.get(field));
                    }
                }
                errors.add(new RowError(row, reason.toString()));
            }
        }
        return new ParsedCsv(dataRows.size(), valid, errors);
    }

    private static Map<String, Integer> readHeader(CSVRecord header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).replace("﻿", "").trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty() && i == header.size() - 1) {
                continue; // tolerate a trailing comma
            }
            if (IGNORED.contains(name)) {
                continue;
            }
            if (!ALLOWED.contains(name) || columns.putIfAbsent(name, i) != null) {
                throw new BadRequestException("Wrong CSV header. Expected: " + EXPECTED_HEADER);
            }
        }
        if (!columns.keySet().containsAll(REQUIRED)) {
            throw new BadRequestException("Wrong CSV header. Expected: " + EXPECTED_HEADER);
        }
        return columns;
    }

    private static boolean isBlank(CSVRecord r) {
        for (String v : r) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Trimmed cell value, null when the column is absent or the cell is blank. */
    private static String value(CSVRecord rec, Integer index) {
        if (index == null || index >= rec.size()) {
            return null;
        }
        return ExpenseValidator.clean(rec.get(index));
    }

    static LocalDate parseDate(String raw) {
        String s = raw.trim().replace('/', '-');
        for (DateTimeFormatter f : List.of(ISO, DMY)) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Strips ₹, "," and spaces: "₹1,250.50" → 1250.50. Null if not a number. */
    static BigDecimal parseAmount(String raw) {
        String s = raw.replace("₹", "").replace(",", "").replace(" ", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
