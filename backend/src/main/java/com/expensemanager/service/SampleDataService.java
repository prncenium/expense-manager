package com.expensemanager.service;

import com.expensemanager.dto.UploadResult;
import com.expensemanager.exception.ConflictException;
import com.expensemanager.repository.ExpenseRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the bundled sample-data/expenses.csv through the normal CSV import (SPEC §7). */
@Service
public class SampleDataService {

    static final String RESOURCE = "sample-data/expenses.csv";

    private final ExpenseRepository expenseRepository;
    private final CsvImportService importService;

    public SampleDataService(ExpenseRepository expenseRepository, CsvImportService importService) {
        this.expenseRepository = expenseRepository;
        this.importService = importService;
    }

    @Transactional
    public UploadResult load() {
        if (expenseRepository.count() > 0) {
            throw new ConflictException("Sample data can only be loaded when there are no expenses");
        }
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return importService.importCsv(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Sample CSV missing from the build", e);
        }
    }
}
