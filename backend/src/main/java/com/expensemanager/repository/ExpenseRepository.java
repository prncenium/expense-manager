package com.expensemanager.repository;

import com.expensemanager.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** Duplicate = same date + same amount + same vendor (case/space-insensitive), see SPEC §3.11. */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM expenses
              WHERE expense_date = :date AND amount = :amount
                AND lower(trim(vendor)) = lower(trim(:vendor)))
            """, nativeQuery = true)
    boolean existsDuplicate(@Param("date") LocalDate date,
                            @Param("amount") BigDecimal amount,
                            @Param("vendor") String vendor);

    /** Existing duplicate keys in a date range, for a bulk check during CSV import. */
    @Query(value = """
            SELECT expense_date, amount, lower(trim(vendor))
            FROM expenses
            WHERE expense_date BETWEEN :from AND :to
            """, nativeQuery = true)
    List<Object[]> findDuplicateKeys(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
