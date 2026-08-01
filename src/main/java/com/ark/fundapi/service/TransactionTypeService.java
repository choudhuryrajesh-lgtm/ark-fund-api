package com.ark.fundapi.service;

import com.ark.fundapi.domain.TransactionType;
import com.ark.fundapi.exception.ResourceNotFoundException;
import com.ark.fundapi.repository.TransactionTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TransactionTypeService {

    private final TransactionTypeRepository transactionTypeRepository;

    public TransactionTypeService(TransactionTypeRepository transactionTypeRepository) {
        this.transactionTypeRepository = transactionTypeRepository;
    }

    /** Types a new transaction may be posted with. */
    public List<TransactionType> listActive() {
        return transactionTypeRepository.findByActiveTrueOrderByCodeAsc();
    }

    /** Every known type, including retired ones — used to seed report totals so history still renders. */
    public List<TransactionType> listAll() {
        return transactionTypeRepository.findAllByOrderByCodeAsc();
    }

    /**
     * Resolves a type code to its governing row, rejecting anything unknown or
     * retired. A retired type can still appear in historical reports (it's not
     * deleted), but it can no longer be used on a new or corrected transaction.
     */
    public TransactionType require(String code) {
        return transactionTypeRepository.findById(code)
                .filter(TransactionType::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction type '%s' was not found or is not active".formatted(code)));
    }
}