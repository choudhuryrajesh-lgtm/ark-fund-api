package com.ark.fundapi.repository;

import com.ark.fundapi.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByEmailIgnoreCase(String email);
}
