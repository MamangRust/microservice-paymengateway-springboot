package com.topupservice.topupservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.topupservice.topupservice.entity.Outbox;
import com.topupservice.topupservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}