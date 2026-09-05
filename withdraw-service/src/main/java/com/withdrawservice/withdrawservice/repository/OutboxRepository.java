package com.withdrawservice.withdrawservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.withdrawservice.withdrawservice.entity.Outbox;
import com.withdrawservice.withdrawservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}