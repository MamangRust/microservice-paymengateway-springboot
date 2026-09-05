package com.saldoservice.saldoservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.saldoservice.saldoservice.entity.SaldoMutationOperation;
import java.util.Optional;

public interface SaldoMutationOperationRepository extends JpaRepository<SaldoMutationOperation, String> {
    Optional<SaldoMutationOperation> findByOperationKey(String operationKey);
}