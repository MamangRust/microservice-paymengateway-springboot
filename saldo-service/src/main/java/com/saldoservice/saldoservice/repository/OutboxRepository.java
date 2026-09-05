package com.saldoservice.saldoservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.saldoservice.saldoservice.entity.Outbox;
import com.saldoservice.saldoservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}