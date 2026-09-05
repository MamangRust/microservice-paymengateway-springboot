package com.topupservice.topupservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.topupservice.topupservice.entity.Topup;
import java.util.Optional;

public interface TopupRepository extends JpaRepository<Topup, Long> {
    Optional<Topup> findByIdempotencyKey(String idempotencyKey);
}