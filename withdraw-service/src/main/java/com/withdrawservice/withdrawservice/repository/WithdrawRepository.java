package com.withdrawservice.withdrawservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.withdrawservice.withdrawservice.entity.Withdraw;
import java.util.Optional;

public interface WithdrawRepository extends JpaRepository<Withdraw, Long> {
    Optional<Withdraw> findByIdempotencyKey(String idempotencyKey);
}