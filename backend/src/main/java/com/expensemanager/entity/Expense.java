package com.expensemanager.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "expenses")
public class Expense {

    public enum Source { MANUAL, CSV }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 150)
    private String vendor;

    @Column(length = 500)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Short categoryId;

    /** Nickname from the matching rule, only in Other (SPEC A.2). */
    @Column(length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Expense() {
    }

    public Expense(LocalDate date, BigDecimal amount, String vendor, String description,
                   Short categoryId, String label, Source source) {
        this.date = date;
        this.amount = amount;
        this.vendor = vendor;
        this.description = description;
        this.categoryId = categoryId;
        this.label = label;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getVendor() {
        return vendor;
    }

    public String getDescription() {
        return description;
    }

    public Short getCategoryId() {
        return categoryId;
    }

    public String getLabel() {
        return label;
    }

    public Source getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
