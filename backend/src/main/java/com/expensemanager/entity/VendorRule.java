package com.expensemanager.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vendor_rules")
public class VendorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored trimmed and lower-case. */
    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "category_id", nullable = false)
    private Short categoryId;

    /** Nickname, only with category Other (SPEC A.2). */
    @Column(length = 50)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected VendorRule() {
    }

    public VendorRule(String keyword, Short categoryId) {
        this(keyword, categoryId, null);
    }

    public VendorRule(String keyword, Short categoryId, String label) {
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.label = label;
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Short getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Short categoryId) {
        this.categoryId = categoryId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
