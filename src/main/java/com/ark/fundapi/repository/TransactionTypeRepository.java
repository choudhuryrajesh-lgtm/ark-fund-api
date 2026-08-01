package com.ark.fundapi.repository;

import com.ark.fundapi.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    /** Every known type, including retired ones — historical transactions may still reference them. */
    List<TransactionType> findAllByOrderByCodeAsc();

    /** Types available for new transactions. */
    List<TransactionType> findByActiveTrueOrderByCodeAsc();
}