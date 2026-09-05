package com.common.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Shared Kafka configuration — mirrors Quarkus payment gateway Kafka setup.
 * Topics: stats.payment.<domain>.event, email-service-topic-*, card internal topics.
 */
@Configuration
public class KafkaCommonConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaCommonConfig.class);

    // === Stats topics (stats-writer consumer) ===
    public static final String TOPIC_STATS_CARD = "stats.payment.card.event";
    public static final String TOPIC_STATS_MERCHANT = "stats.payment.merchant.event";
    public static final String TOPIC_STATS_SALDO = "stats.payment.saldo.event";
    public static final String TOPIC_STATS_TOPUP = "stats.payment.topup.event";
    public static final String TOPIC_STATS_TRANSACTION = "stats.payment.transaction.event";
    public static final String TOPIC_STATS_TRANSFER = "stats.payment.transfer.event";
    public static final String TOPIC_STATS_WITHDRAW = "stats.payment.withdraw.event";

    // === Email topics (email-service consumer) ===
    public static final String TOPIC_EMAIL_REGISTER = "email-service-topic-auth-register";
    public static final String TOPIC_EMAIL_FORGOT_PASSWORD = "email-service-topic-auth-forgot-password";
    public static final String TOPIC_EMAIL_VERIFY_SUCCESS = "email-service-topic-auth-verify-code-success";
    public static final String TOPIC_EMAIL_SALDO_CREATE = "email-service-topic-saldo-create";
    public static final String TOPIC_EMAIL_TOPUP_CREATE = "email-service-topic-topup-create";
    public static final String TOPIC_EMAIL_TRANSACTION_CREATE = "email-service-topic-transaction-create";
    public static final String TOPIC_EMAIL_TRANSFER_CREATE = "email-service-topic-transfer-create";
    public static final String TOPIC_EMAIL_WITHDRAW_CREATE = "email-service-topic-withdraw-create";
    public static final String TOPIC_EMAIL_MERCHANT_CREATE = "email-service-topic-merchant-create";
    public static final String TOPIC_EMAIL_MERCHANT_UPDATE_STATUS = "email-service-topic-merchant-update-status";
    public static final String TOPIC_EMAIL_MERCHANT_DOC_CREATE = "email-service-topic-merchant-document-create";
    public static final String TOPIC_EMAIL_MERCHANT_DOC_UPDATE_STATUS = "email-service-topic-merchant-document-update-status";

    // === Card internal topics ===
    public static final String TOPIC_CARD_TXN_CREATED = "card.txn.created";
    public static final String TOPIC_CARD_FRAUD_ALERT = "card.fraud.alert";
    public static final String TOPIC_CARD_PAYMENT_POSTED = "card.payment.posted";
    public static final String TOPIC_CARD_STATEMENT_GENERATED = "card.statement.generated";

    // === Saldo lifecycle ===
    public static final String TOPIC_SALDO_CREATE = "saldo-service-topic-create-saldo";

    public static final String TOPIC_NOTIFICATION = "notification-topic";

    public static final int PARTITIONS = 3;
    public static final short REPLICATION = 1;

    @Bean
    @ConditionalOnMissingBean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // === Topic definitions ===

    @Bean
    public NewTopic topicStatsCard() {
        return TopicBuilder.name(TOPIC_STATS_CARD).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsMerchant() {
        return TopicBuilder.name(TOPIC_STATS_MERCHANT).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsSaldo() {
        return TopicBuilder.name(TOPIC_STATS_SALDO).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsTopup() {
        return TopicBuilder.name(TOPIC_STATS_TOPUP).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsTransaction() {
        return TopicBuilder.name(TOPIC_STATS_TRANSACTION).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsTransfer() {
        return TopicBuilder.name(TOPIC_STATS_TRANSFER).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicStatsWithdraw() {
        return TopicBuilder.name(TOPIC_STATS_WITHDRAW).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailRegister() {
        return TopicBuilder.name(TOPIC_EMAIL_REGISTER).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailForgotPassword() {
        return TopicBuilder.name(TOPIC_EMAIL_FORGOT_PASSWORD).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailVerifySuccess() {
        return TopicBuilder.name(TOPIC_EMAIL_VERIFY_SUCCESS).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailSaldoCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_SALDO_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailTopupCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_TOPUP_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailTransactionCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_TRANSACTION_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailTransferCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_TRANSFER_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailWithdrawCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_WITHDRAW_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailMerchantCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_MERCHANT_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailMerchantUpdateStatus() {
        return TopicBuilder.name(TOPIC_EMAIL_MERCHANT_UPDATE_STATUS).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailMerchantDocCreate() {
        return TopicBuilder.name(TOPIC_EMAIL_MERCHANT_DOC_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicEmailMerchantDocUpdateStatus() {
        return TopicBuilder.name(TOPIC_EMAIL_MERCHANT_DOC_UPDATE_STATUS).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicCardTxnCreated() {
        return TopicBuilder.name(TOPIC_CARD_TXN_CREATED).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicCardFraudAlert() {
        return TopicBuilder.name(TOPIC_CARD_FRAUD_ALERT).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicCardPaymentPosted() {
        return TopicBuilder.name(TOPIC_CARD_PAYMENT_POSTED).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicCardStatementGenerated() {
        return TopicBuilder.name(TOPIC_CARD_STATEMENT_GENERATED).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicSaldoCreate() {
        return TopicBuilder.name(TOPIC_SALDO_CREATE).partitions(PARTITIONS).replicas(REPLICATION).build();
    }

    @Bean
    public NewTopic topicNotification() {
        return TopicBuilder.name(TOPIC_NOTIFICATION).partitions(PARTITIONS).replicas(REPLICATION).build();
    }
}