package com.transactionservice.transactionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.transactionservice.transactionservice.entity.Transaction;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}