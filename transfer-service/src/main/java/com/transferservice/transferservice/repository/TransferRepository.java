package com.transferservice.transferservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.transferservice.transferservice.entity.Transfer;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}