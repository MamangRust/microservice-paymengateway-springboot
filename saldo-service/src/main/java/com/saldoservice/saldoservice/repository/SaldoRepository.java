package com.saldoservice.saldoservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.saldoservice.saldoservice.entity.Saldo;
import java.util.Optional;

public interface SaldoRepository extends JpaRepository<Saldo, Long> {
    Optional<Saldo> findByCardNumber(String cardNumber);
}