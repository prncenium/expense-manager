package com.expensemanager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {

    public static final String OTHER = "Other";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    protected Category() {
    }

    public Category(Short id, String name) {
        this.id = id;
        this.name = name;
    }

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
