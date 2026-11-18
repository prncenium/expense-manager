package com.expensemanager.controller;

import com.expensemanager.dto.ExpensePage;
import com.expensemanager.dto.ExpenseRequest;
import com.expensemanager.dto.ExpenseView;
import com.expensemanager.dto.UploadResult;
import com.expensemanager.service.CsvImportService;
import com.expensemanager.service.ExpenseFilter;
import com.expensemanager.service.ExpenseService;
import com.expensemanager.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CsvImportService importService;
    private final ExportService exportService;

    public ExpenseController(ExpenseService expenseService, CsvImportService importService,
                             ExportService exportService) {
        this.expenseService = expenseService;
        this.importService = importService;
        this.exportService = exportService;
    }

    @GetMapping
    public ExpensePage list(@RequestParam(required = false) String month,
                            @RequestParam(required = false) Short categoryId,
                            @RequestParam(required = false) String label,
                            @RequestParam(defaultValue = "false") boolean anomaliesOnly,
                            @RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "" + ExpenseService.PAGE_SIZE_DEFAULT) int size) {
        return expenseService.list(ExpenseFilter.of(month, categoryId, label, anomaliesOnly, q), page, size);
    }

    /** Same filters as the list, every page, as a CSV download (SPEC D). */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String month,
                       @RequestParam(required = false) Short categoryId,
                       @RequestParam(required = false) String label,
                       @RequestParam(defaultValue = "false") boolean anomaliesOnly,
                       @RequestParam(required = false) String q,
                       HttpServletResponse response) throws IOException {
        ExpenseFilter filter = ExpenseFilter.of(month, categoryId, label, anomaliesOnly, q);
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + ExportService.fileName(filter) + "\"");
        exportService.write(filter, response.getWriter());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView create(@RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        expenseService.delete(id);
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestPart("file") MultipartFile file) {
        return importService.upload(file);
    }
}
