package com.expensemanager.service;

import com.expensemanager.dto.ExpensePage;
import com.expensemanager.dto.ExpenseRequest;
import com.expensemanager.dto.ExpenseView;
import com.expensemanager.entity.Expense;
import com.expensemanager.exception.BadRequestException;
import com.expensemanager.exception.NotFoundException;
import com.expensemanager.exception.ValidationException;
import com.expensemanager.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseQueryRepository queryRepository;
    private final CategorizationService categorizationService;
    private final ExpenseValidator validator;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseQueryRepository queryRepository,
                          CategorizationService categorizationService, ExpenseValidator validator) {
        this.expenseRepository = expenseRepository;
        this.queryRepository = queryRepository;
        this.categorizationService = categorizationService;
        this.validator = validator;
    }

    public static final int PAGE_SIZE_DEFAULT = 50;
    public static final int PAGE_SIZE_MAX = 100;

    /** One page of expenses plus totals over all matching rows (SPEC C). */
    @Transactional(readOnly = true)
    public ExpensePage list(ExpenseFilter filter, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw new BadRequestException("size must be 1–" + PAGE_SIZE_MAX);
        }
        ExpenseQueryRepository.Totals totals = queryRepository.totals(filter);
        List<ExpenseView> items = totals.count() == 0
                ? List.of()
                : queryRepository.find(filter, null, size, (long) page * size);
        int totalPages = (int) ((totals.count() + size - 1) / size);
        return new ExpensePage(items, page, size, totals.count(), totalPages, totals.amount());
    }

    /**
     * Saves one expense. A duplicate (§3.11) is still saved; the response says
     * possibleDuplicate=true so the UI can warn.
     */
    @Transactional
    public ExpenseView create(ExpenseRequest req) {
        Map<String, String> errors = validator.validate(req.date(), req.amount(), req.vendor(), req.description());
        if (!errors.isEmpty()) {
            throw new ValidationException("Please fix the highlighted fields", errors);
        }
        String vendor = ExpenseValidator.clean(req.vendor());
        BigDecimal amount = req.amount().setScale(2, RoundingMode.UNNECESSARY);

        boolean duplicate = expenseRepository.existsDuplicate(req.date(), amount, vendor);
        CategorizationService.Match match = categorizationService.match(vendor);
        Expense saved = expenseRepository.saveAndFlush(new Expense(
                req.date(), amount, vendor, ExpenseValidator.clean(req.description()),
                match.categoryId(), match.label(), Expense.Source.MANUAL));

        return queryRepository.findById(saved.getId()).withPossibleDuplicate(duplicate);
    }

    @Transactional
    public void delete(long id) {
        if (!expenseRepository.existsById(id)) {
            throw new NotFoundException("Expense " + id + " not found");
        }
        expenseRepository.deleteById(id);
    }
}
