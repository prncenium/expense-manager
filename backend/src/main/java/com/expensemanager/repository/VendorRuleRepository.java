package com.expensemanager.repository;

import com.expensemanager.entity.VendorRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRuleRepository extends JpaRepository<VendorRule, Long> {
    Optional<VendorRule> findByKeyword(String keyword);
}
