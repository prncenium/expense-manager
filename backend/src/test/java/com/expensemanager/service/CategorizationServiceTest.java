package com.expensemanager.service;

import com.expensemanager.entity.Category;
import com.expensemanager.entity.VendorRule;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.repository.VendorRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CategorizationServiceTest {

    private static final short FOOD = 1, SHOPPING = 4, ENTERTAINMENT = 5, OTHER = 9;

    private VendorRuleRepository rules;
    private CategorizationService service;

    @BeforeEach
    void setUp() {
        rules = mock(VendorRuleRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        when(categories.findByName("Other")).thenReturn(Optional.of(new Category(OTHER, "Other")));
        when(rules.findAll()).thenReturn(List.of(
                new VendorRule("swiggy", FOOD),
                new VendorRule("amazon", SHOPPING),
                new VendorRule("amazon prime", ENTERTAINMENT),
                new VendorRule("cult.fit", OTHER, "Gym")));
        service = new CategorizationService(rules, categories);
    }

    @Test
    void exactMatch() {
        assertThat(service.categorize("swiggy")).isEqualTo(FOOD);
    }

    @Test
    void ignoresCaseAndSurroundingSpaces() {
        assertThat(service.categorize("  SwIgGy  ")).isEqualTo(FOOD);
    }

    @Test
    void partialMatchInsideLongerVendorName() {
        assertThat(service.categorize("SWIGGY INSTAMART")).isEqualTo(FOOD);
    }

    @Test
    void longestKeywordWins() {
        assertThat(service.categorize("Amazon Prime Video")).isEqualTo(ENTERTAINMENT);
        assertThat(service.categorize("Amazon Pay")).isEqualTo(SHOPPING);
    }

    @Test
    void noMatchGoesToOther() {
        assertThat(service.categorize("Chai Point")).isEqualTo(OTHER);
    }

    @Test
    void rulesAreCachedUntilInvalidated() {
        service.categorize("swiggy");
        service.categorize("zomato");
        verify(rules, times(1)).findAll();

        service.invalidate();
        service.categorize("swiggy");
        verify(rules, times(2)).findAll();
    }

    @Test
    void matchCarriesTheRuleLabel() {
        assertThat(service.match("CULT.FIT Koramangala"))
                .isEqualTo(new CategorizationService.Match(OTHER, "Gym", true));
        assertThat(service.match("Chai Point"))
                .isEqualTo(new CategorizationService.Match(OTHER, null, false));
        assertThat(service.match("Swiggy").label()).isNull();
    }
}
