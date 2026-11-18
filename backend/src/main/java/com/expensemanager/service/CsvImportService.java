package com.expensemanager.service;

import com.expensemanager.dto.UploadResult;
import com.expensemanager.dto.UploadResult.RowError;
import com.expensemanager.entity.Expense;
import com.expensemanager.exception.BadRequestException;
import com.expensemanager.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * CSV import used by upload and sample data (SPEC §8): valid, non-duplicate rows are saved in
 * one transaction; every other row is reported.
 */
@Service
public class CsvImportService {

    public static final String DUPLICATE = "duplicate";

    private final CsvParser parser;
    private final ExpenseRepository expenseRepository;
    private final ExpenseQueryRepository queryRepository;
    private final CategorizationService categorizationService;

    public CsvImportService(CsvParser parser, ExpenseRepository expenseRepository,
                            ExpenseQueryRepository queryRepository, CategorizationService categorizationService) {
        this.parser = parser;
        this.expenseRepository = expenseRepository;
        this.queryRepository = queryRepository;
        this.categorizationService = categorizationService;
    }

    @Transactional
    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a non-empty .csv file");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Only .csv files are accepted");
        }
        try (InputStream in = file.getInputStream()) {
            return importCsv(in);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    @Transactional
    public UploadResult importCsv(InputStream in) {
        CsvParser.ParsedCsv parsed = parser.parse(in);
        List<RowError> errors = new ArrayList<>(parsed.errors());

        Set<String> known = existingKeys(parsed.valid());
        List<Expense> toSave = new ArrayList<>();
        int duplicates = 0;
        for (CsvParser.CsvRow row : parsed.valid()) {
            BigDecimal amount = row.amount().setScale(2, RoundingMode.UNNECESSARY);
            // Set.add is false when the key is already in the DB or earlier in this file
            if (!known.add(key(row.date(), amount, row.vendor()))) {
                duplicates++;
                errors.add(new RowError(row.row(), DUPLICATE));
                continue;
            }
            CategorizationService.Match match = categorizationService.match(row.vendor());
            toSave.add(new Expense(row.date(), amount, row.vendor(), row.description(),
                    match.categoryId(), match.label(), Expense.Source.CSV));
        }

        List<Long> savedIds = expenseRepository.saveAllAndFlush(toSave).stream().map(Expense::getId).toList();
        errors.sort(Comparator.comparingInt(RowError::row));

        return new UploadResult(parsed.total(), savedIds.size(), errors.size(), duplicates, errors,
                queryRepository.countAnomalies(savedIds));
    }

    private Set<String> existingKeys(List<CsvParser.CsvRow> rows) {
        Set<String> keys = new HashSet<>();
        if (rows.isEmpty()) {
            return keys;
        }
        LocalDate from = rows.stream().map(CsvParser.CsvRow::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = rows.stream().map(CsvParser.CsvRow::date).max(Comparator.naturalOrder()).orElseThrow();
        for (Object[] r : expenseRepository.findDuplicateKeys(from, to)) {
            LocalDate date = r[0] instanceof Date d ? d.toLocalDate() : (LocalDate) r[0];
            keys.add(key(date, (BigDecimal) r[1], (String) r[2]));
        }
        return keys;
    }

    /** date + amount + lower(trim(vendor)), the same normalisation as idx_expenses_dup. */
    static String key(LocalDate date, BigDecimal amount, String vendor) {
        return date + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "|" + vendor.trim().toLowerCase(Locale.ROOT);
    }
}
