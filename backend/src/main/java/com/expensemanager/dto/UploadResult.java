package com.expensemanager.dto;

import java.util.List;

/**
 * Result of a CSV import. {@code skipped} counts every row not saved (invalid + duplicate);
 * {@code duplicates} is the duplicate part of it. {@code errors} lists each skipped row.
 */
public record UploadResult(int total, int saved, int skipped, int duplicates,
                           List<RowError> errors, int anomaliesFound) {

    public record RowError(int row, String reason) {
    }
}
