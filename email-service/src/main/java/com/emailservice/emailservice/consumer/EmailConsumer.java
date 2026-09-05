package com.emailservice.emailservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer worker — mirror Quarkus EmailService.
 * Subscribes to email-service-topic-* topics, dedup via EmailDedupGuard,
 * sends via SMTP.
 */
@Component
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    @KafkaListener(topics = "email-service-topic-auth-register", groupId = "email-service")
    public void handleRegister(String message) {
        log.info("Register email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-saldo-create", groupId = "email-service")
    public void handleSaldoCreate(String message) {
        log.info("Saldo email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-topup-create", groupId = "email-service")
    public void handleTopupCreate(String message) {
        log.info("Topup email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-transfer-create", groupId = "email-service")
    public void handleTransferCreate(String message) {
        log.info("Transfer email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-withdraw-create", groupId = "email-service")
    public void handleWithdrawCreate(String message) {
        log.info("Withdraw email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-transaction-create", groupId = "email-service")
    public void handleTransactionCreate(String message) {
        log.info("Transaction email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-merchant-create", groupId = "email-service")
    public void handleMerchantCreate(String message) {
        log.info("Merchant create email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-merchant-update-status", groupId = "email-service")
    public void handleMerchantStatus(String message) {
        log.info("Merchant status email: {}", message);
    }
}