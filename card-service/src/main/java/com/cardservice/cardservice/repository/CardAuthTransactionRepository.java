package com.cardservice.cardservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.cardservice.cardservice.entity.CardAuthTransaction;
import java.util.List;
import java.util.Optional;

public interface CardAuthTransactionRepository extends JpaRepository<CardAuthTransaction, Long> {
    List<CardAuthTransaction> findByCardNumber(String cardNumber);
    Optional<CardAuthTransaction> findByIdempotencyKey(String idempotencyKey);
}