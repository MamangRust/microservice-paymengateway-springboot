package com.emailservice.emailservice.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmailConsumer}. The consumer only logs the received
 * Kafka messages, so the honest contract to verify is: it can be instantiated
 * with no dependencies and consumes messages of any content (including empty
 * strings) without throwing.
 *
 * Payment flavor: 8 listeners (register, saldo/topup/transfer/withdraw/
 * transaction/merchant-create/merchant-status) — the ecommerce sibling's
 * consumer instead subscribes to the forgot-password topic, so this test is
 * adapted, not copied.
 */
class EmailConsumerTest {

    private final EmailConsumer emailConsumer = new EmailConsumer();

    @Test
    void handleRegister_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleRegister(
                "{\"userId\":\"u-1\",\"username\":\"johndoe\",\"email\":\"john@example.com\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleSaldoCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleSaldoCreate(
                "{\"userId\":\"u-1\",\"email\":\"john@example.com\",\"amount\":150000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleTopupCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleTopupCreate(
                "{\"topupId\":\"t-1\",\"email\":\"john@example.com\",\"amount\":50000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleTransferCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleTransferCreate(
                "{\"transferId\":\"tr-1\",\"email\":\"john@example.com\",\"amount\":75000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleWithdrawCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleWithdrawCreate(
                "{\"withdrawId\":\"w-1\",\"email\":\"john@example.com\",\"amount\":25000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleTransactionCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleTransactionCreate(
                "{\"transactionId\":\"TRX-1\",\"email\":\"john@example.com\",\"total\":25000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleMerchantCreate_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleMerchantCreate(
                "{\"merchantId\":\"m-1\",\"email\":\"merchant@example.com\",\"name\":\"Toko A\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleMerchantStatus_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleMerchantStatus(
                "{\"merchantId\":\"m-1\",\"email\":\"merchant@example.com\",\"status\":\"APPROVED\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void allListeners_acceptEmptyStringMessage() {
        assertThatCode(() -> {
            emailConsumer.handleRegister("");
            emailConsumer.handleSaldoCreate("");
            emailConsumer.handleTopupCreate("");
            emailConsumer.handleTransferCreate("");
            emailConsumer.handleWithdrawCreate("");
            emailConsumer.handleTransactionCreate("");
            emailConsumer.handleMerchantCreate("");
            emailConsumer.handleMerchantStatus("");
        }).doesNotThrowAnyException();
    }
}
