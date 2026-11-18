package com.expensemanager.service;

import com.expensemanager.dto.RuleDto;
import com.expensemanager.dto.RuleRequest;
import com.expensemanager.entity.Category;
import com.expensemanager.entity.VendorRule;
import com.expensemanager.exception.ConflictException;
import com.expensemanager.exception.NotFoundException;
import com.expensemanager.exception.ValidationException;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.repository.VendorRuleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vendor rule CRUD (SPEC §5). Keywords are trimmed, lower-cased, 2–100 chars and unique.
 * Not @Transactional on purpose: each save commits before the categorization cache is cleared.
 */
@Service
public class RuleService {

    public static final int KEYWORD_MIN = 2;
    public static final int KEYWORD_MAX = 100;
    public static final int LABEL_MAX = 50;

    private final VendorRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationService categorizationService;
    private final RecategorizeService recategorizeService;

    public RuleService(VendorRuleRepository ruleRepository, CategoryRepository categoryRepository,
                       CategorizationService categorizationService, RecategorizeService recategorizeService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationService = categorizationService;
        this.recategorizeService = recategorizeService;
    }

    public List<RuleDto> list() {
        Map<Short, String> names = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return ruleRepository.findAll().stream()
                .sorted(Comparator.comparing(VendorRule::getKeyword))
                .map(r -> toDto(r, names.get(r.getCategoryId()), null))
                .toList();
    }

    public RuleDto create(RuleRequest req) {
        String keyword = normalizeKeyword(req.keyword());
        Category category = requireCategory(req.categoryId());
        String label = normalizeLabel(req.label(), category);
        ensureUnique(keyword, null);
        return save(new VendorRule(keyword, category.getId(), label), category);
    }

    public RuleDto update(long id, RuleRequest req) {
        VendorRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rule " + id + " not found"));
        String keyword = normalizeKeyword(req.keyword());
        Category category = requireCategory(req.categoryId());
        String label = normalizeLabel(req.label(), category);
        ensureUnique(keyword, id);
        rule.setKeyword(keyword);
        rule.setCategoryId(category.getId());
        rule.setLabel(label);
        return save(rule, category);
    }

    public void delete(long id) {
        if (!ruleRepository.existsById(id)) {
            throw new NotFoundException("Rule " + id + " not found");
        }
        ruleRepository.deleteById(id);
        categorizationService.invalidate();
    }

    /** Trim + lower-case; rejects anything outside 2–100 chars (one letter would match nearly every vendor). */
    public static String normalizeKeyword(String raw) {
        String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (k.length() < KEYWORD_MIN || k.length() > KEYWORD_MAX) {
            throw new ValidationException("keyword", "must be " + KEYWORD_MIN + "–" + KEYWORD_MAX + " characters");
        }
        return k;
    }

    /** Trimmed, blank → null, max 50 chars, only allowed with category Other (SPEC A.2). */
    public static String normalizeLabel(String raw, Category category) {
        String label = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (label.isEmpty()) {
            return null;
        }
        if (!Category.OTHER.equals(category.getName())) {
            throw new ValidationException("label", "is only for Other");
        }
        if (label.length() > LABEL_MAX) {
            throw new ValidationException("label", "must be at most " + LABEL_MAX + " characters");
        }
        return label;
    }

    private Category requireCategory(Short categoryId) {
        if (categoryId == null) {
            throw new ValidationException("categoryId", "is required");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ValidationException("categoryId", "unknown category"));
    }

    private void ensureUnique(String keyword, Long selfId) {
        ruleRepository.findByKeyword(keyword)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw duplicate(keyword);
                });
    }

    private RuleDto save(VendorRule rule, Category category) {
        VendorRule saved;
        try {
            saved = ruleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            // two requests raced past ensureUnique; the unique constraint caught it
            throw duplicate(rule.getKeyword());
        }
        categorizationService.invalidate();
        int moved = recategorizeService.reapplyToOther();
        return toDto(saved, category.getName(), moved);
    }

    private static ConflictException duplicate(String keyword) {
        return new ConflictException("A rule for keyword '" + keyword + "' already exists");
    }

    private static RuleDto toDto(VendorRule r, String categoryName, Integer recategorized) {
        return new RuleDto(r.getId(), r.getKeyword(), r.getCategoryId(), categoryName, r.getLabel(), recategorized);
    }
}
