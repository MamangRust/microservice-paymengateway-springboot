package com.transferservice.transferservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.transferservice.transferservice.entity.Outbox;
import com.transferservice.transferservice.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}