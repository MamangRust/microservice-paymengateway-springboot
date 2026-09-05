package com.transactionservice.transactionservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.transactionservice.transactionservice.entity.Outbox;
import com.transactionservice.transactionservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}