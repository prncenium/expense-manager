package com.expensemanager.service;

import com.expensemanager.entity.Category;
import com.expensemanager.entity.VendorRule;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.repository.VendorRuleRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Picks a category for a vendor name (SPEC §5): case-insensitive "vendor contains keyword",
 * longest keyword wins, no match means Other. Rules are cached in memory; RuleService calls
 * {@link #invalidate()} after every rule change.
 */
@Service
public class CategorizationService {

    private final VendorRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    private volatile Snapshot cache;

    public CategorizationService(VendorRuleRepository ruleRepository, CategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    /** Returns the category id for this vendor. */
    public Short categorize(String vendor) {
        return match(vendor).categoryId();
    }

    /** Category + label (nickname) of the winning rule; no match → Other, no label, matched=false. */
    public Match match(String vendor) {
        Snapshot snapshot = snapshot();
        String v = vendor == null ? "" : vendor.trim().toLowerCase(Locale.ROOT);
        // rules are sorted longest keyword first, so the first hit is the winner
        for (VendorRule rule : snapshot.rules()) {
            if (v.contains(rule.getKeyword())) {
                return new Match(rule.getCategoryId(), rule.getLabel(), true);
            }
        }
        return new Match(snapshot.otherCategoryId(), null, false);
    }

    public Short otherCategoryId() {
        return snapshot().otherCategoryId();
    }

    public void invalidate() {
        cache = null;
    }

    private Snapshot snapshot() {
        Snapshot s = cache;
        if (s == null) {
            List<VendorRule> rules = ruleRepository.findAll().stream()
                    .sorted(Comparator.comparingInt((VendorRule r) -> r.getKeyword().length()).reversed()
                            .thenComparing(VendorRule::getKeyword))
                    .toList();
            Short otherId = categoryRepository.findByName(Category.OTHER)
                    .map(Category::getId)
                    .orElseThrow(() -> new IllegalStateException("Seed category 'Other' is missing"));
            s = new Snapshot(rules, otherId);
            cache = s;
        }
        return s;
    }

    public record Match(Short categoryId, String label, boolean matched) {
    }

    private record Snapshot(List<VendorRule> rules, Short otherCategoryId) {
    }
}
