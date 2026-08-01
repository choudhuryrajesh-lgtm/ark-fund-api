package com.ark.fundapi.repository;

import com.ark.fundapi.domain.Investor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvestorRepository extends JpaRepository<Investor, UUID> {

    Optional<Investor> findByIdAndClientId(UUID id, UUID clientId);

    Page<Investor> findByClientId(UUID clientId, Pageable pageable);

    boolean existsByClientIdAndEmailIgnoreCase(UUID clientId, String email);

    boolean existsByClientIdAndEmailIgnoreCaseAndIdNot(UUID clientId, String email, UUID id);
}
