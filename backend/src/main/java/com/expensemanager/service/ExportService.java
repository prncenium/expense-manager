package com.expensemanager.service;

import com.expensemanager.dto.ExpenseView;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

/** CSV export of the filtered expenses (SPEC D), streamed row by row. */
@Service
public class ExportService {

    public static final int MAX_ROWS = 100_000;
    static final String[] HEADER = {"date", "amount", "vendor", "description", "category", "label", "high_expense"};

    private final ExpenseQueryRepository queryRepository;

    public ExportService(ExpenseQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public void write(ExpenseFilter filter, Writer out) throws IOException {
        CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setRecordSeparator("\r\n").get());
        csv.printRecord((Object[]) HEADER);
        try {
            queryRepository.stream(filter, MAX_ROWS, e -> print(csv, e));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        csv.flush();
    }

    private static void print(CSVPrinter csv, ExpenseView e) {
        try {
            csv.printRecord(e.date().toString(), e.amount().toPlainString(), safe(e.vendor()),
                    safe(e.description()), e.category(), safe(e.label()), e.anomaly() ? "yes" : "no");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** CSV-injection guard: a cell Excel would read as a formula gets a leading apostrophe. */
    static String safe(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        char c = s.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' ? "'" + s : s;
    }

    /** "expenses-2026-11.csv" or "expenses-all.csv". */
    public static String fileName(ExpenseFilter filter) {
        return "expenses-" + (filter.month() == null ? "all" : filter.month().toString()) + ".csv";
    }
}
