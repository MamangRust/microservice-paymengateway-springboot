package com.cardservice.cardservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.cardservice.cardservice.entity.Card;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByCardNumber(String cardNumber);
    List<Card> findByUserId(Integer userId);
}