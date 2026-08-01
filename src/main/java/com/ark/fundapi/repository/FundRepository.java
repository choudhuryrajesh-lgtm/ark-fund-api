package com.ark.fundapi.repository;

import com.ark.fundapi.domain.Fund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundRepository extends JpaRepository<Fund, UUID> {

    // Every lookup is tenant-scoped by client id rather than a bare findById.
    // Requesting an id that belongs to another tenant then returns 404 instead
    // of leaking that the resource exists.
    Optional<Fund> findByIdAndClientId(UUID id, UUID clientId);

    Page<Fund> findByClientId(UUID clientId, Pageable pageable);

    /** Unpaged variant used by the portfolio report, which rolls up every fund. */
    List<Fund> findByClientIdOrderByNameAsc(UUID clientId);

    boolean existsByClientIdAndNameIgnoreCase(UUID clientId, String name);

    boolean existsByClientIdAndNameIgnoreCaseAndIdNot(UUID clientId, String name, UUID id);
}
