package com.expensemanager.service;

import com.expensemanager.controller.RuleController;
import com.expensemanager.dto.RuleRequest;
import com.expensemanager.entity.Category;
import com.expensemanager.entity.VendorRule;
import com.expensemanager.exception.ConflictException;
import com.expensemanager.exception.GlobalExceptionHandler;
import com.expensemanager.exception.ValidationException;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.repository.VendorRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleValidationTest {

    private static final short FOOD = 1;

    private VendorRuleRepository rules;
    private CategorizationService categorization;
    private RuleService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rules = mock(VendorRuleRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        categorization = mock(CategorizationService.class);
        when(categories.findById(FOOD)).thenReturn(Optional.of(new Category(FOOD, "Food")));
        when(rules.findByKeyword(any())).thenReturn(Optional.empty());
        when(rules.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new RuleService(rules, categories, categorization, mock(RecategorizeService.class));
        mvc = MockMvcBuilders.standaloneSetup(new RuleController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void oneCharacterKeywordIsRejected() {
        assertThatThrownBy(() -> service.create(new RuleRequest(" a ", FOOD, null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors()).containsKey("keyword"));
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void oneCharacterKeywordGives400WithFieldError() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"a\",\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.keyword").exists());
    }

    @Test
    void keywordIsTrimmedAndLowerCased() {
        assertThat(service.create(new RuleRequest("  Dominos ", FOOD, null)).keyword()).isEqualTo("dominos");
        verify(categorization).invalidate();
    }

    @Test
    void duplicateKeywordIsAConflict() {
        when(rules.findByKeyword("swiggy")).thenReturn(Optional.of(mock(VendorRule.class)));
        assertThatThrownBy(() -> service.create(new RuleRequest("SWIGGY", FOOD, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateKeywordGives409() throws Exception {
        when(rules.findByKeyword("swiggy")).thenReturn(Optional.of(mock(VendorRule.class)));
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"Swiggy\",\"categoryId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A rule for keyword 'swiggy' already exists"));
    }

    @Test
    void unknownCategoryIsRejected() {
        assertThatThrownBy(() -> service.create(new RuleRequest("dominos", (short) 99, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void labelOnlyWithOther() {
        assertThatThrownBy(() -> service.create(new RuleRequest("dominos", FOOD, "Pizza")))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors()).containsKey("label"));
    }

    @Test
    void labelIsTrimmedBlankIsNullAndMax50() {
        Category other = new Category((short) 9, "Other");
        assertThat(RuleService.normalizeLabel("  Gym   fees ", other)).isEqualTo("Gym fees");
        assertThat(RuleService.normalizeLabel("   ", other)).isNull();
        assertThat(RuleService.normalizeLabel("  ", new Category(FOOD, "Food"))).isNull();
        assertThatThrownBy(() -> RuleService.normalizeLabel("x".repeat(51), other))
                .isInstanceOf(ValidationException.class);
    }
}
