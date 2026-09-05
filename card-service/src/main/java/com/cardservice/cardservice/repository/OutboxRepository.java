package com.cardservice.cardservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cardservice.cardservice.entity.Outbox;
import com.cardservice.cardservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}