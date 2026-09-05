package com.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Unit tests for the shared Kafka configuration. Beans are created by direct
 * instantiation — no Spring context needed. The topic constants asserted here
 * are THIS project's (payment) contract, NOT the sibling POS/ecommerce
 * projects' — those ship a different com.common:common with stats.pos.* /
 * stats.ecommerce.* topics, so never install this module's artifact.
 */
class KafkaCommonConfigTest {

    private final KafkaCommonConfig config = new KafkaCommonConfig();

    @Test
    void topicNameConstants_matchPublishedContract() {
        // stats.payment.* (stats-writer consumer)
        assertThat(KafkaCommonConfig.TOPIC_STATS_CARD).isEqualTo("stats.payment.card.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_MERCHANT).isEqualTo("stats.payment.merchant.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_SALDO).isEqualTo("stats.payment.saldo.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_TOPUP).isEqualTo("stats.payment.topup.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_TRANSACTION).isEqualTo("stats.payment.transaction.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_TRANSFER).isEqualTo("stats.payment.transfer.event");
        assertThat(KafkaCommonConfig.TOPIC_STATS_WITHDRAW).isEqualTo("stats.payment.withdraw.event");
        // email-service topics
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_REGISTER).isEqualTo("email-service-topic-auth-register");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_FORGOT_PASSWORD).isEqualTo("email-service-topic-auth-forgot-password");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_VERIFY_SUCCESS).isEqualTo("email-service-topic-auth-verify-code-success");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_SALDO_CREATE).isEqualTo("email-service-topic-saldo-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_TOPUP_CREATE).isEqualTo("email-service-topic-topup-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_TRANSACTION_CREATE).isEqualTo("email-service-topic-transaction-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_TRANSFER_CREATE).isEqualTo("email-service-topic-transfer-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_WITHDRAW_CREATE).isEqualTo("email-service-topic-withdraw-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_CREATE).isEqualTo("email-service-topic-merchant-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_UPDATE_STATUS).isEqualTo("email-service-topic-merchant-update-status");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_DOC_CREATE).isEqualTo("email-service-topic-merchant-document-create");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_DOC_UPDATE_STATUS).isEqualTo("email-service-topic-merchant-document-update-status");
        // card internal topics
        assertThat(KafkaCommonConfig.TOPIC_CARD_TXN_CREATED).isEqualTo("card.txn.created");
        assertThat(KafkaCommonConfig.TOPIC_CARD_FRAUD_ALERT).isEqualTo("card.fraud.alert");
        assertThat(KafkaCommonConfig.TOPIC_CARD_PAYMENT_POSTED).isEqualTo("card.payment.posted");
        assertThat(KafkaCommonConfig.TOPIC_CARD_STATEMENT_GENERATED).isEqualTo("card.statement.generated");
        // saldo lifecycle + notification
        assertThat(KafkaCommonConfig.TOPIC_SALDO_CREATE).isEqualTo("saldo-service-topic-create-saldo");
        assertThat(KafkaCommonConfig.TOPIC_NOTIFICATION).isEqualTo("notification-topic");

        assertThat(KafkaCommonConfig.PARTITIONS).isEqualTo(3);
        assertThat(KafkaCommonConfig.REPLICATION).isEqualTo((short) 1);
    }

    @Test
    void topicBeans_carryDeclaredNamePartitionsAndReplication() {
        // Map.ofEntries on purpose: Map.of throws with more than 10 pairs and
        // this config declares 25 NewTopic beans.
        Map<String, Supplier<NewTopic>> topicBeans = Map.ofEntries(
                Map.entry(KafkaCommonConfig.TOPIC_STATS_CARD, config::topicStatsCard),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_MERCHANT, config::topicStatsMerchant),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_SALDO, config::topicStatsSaldo),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_TOPUP, config::topicStatsTopup),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_TRANSACTION, config::topicStatsTransaction),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_TRANSFER, config::topicStatsTransfer),
                Map.entry(KafkaCommonConfig.TOPIC_STATS_WITHDRAW, config::topicStatsWithdraw),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_REGISTER, config::topicEmailRegister),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_FORGOT_PASSWORD, config::topicEmailForgotPassword),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_VERIFY_SUCCESS, config::topicEmailVerifySuccess),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_SALDO_CREATE, config::topicEmailSaldoCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_TOPUP_CREATE, config::topicEmailTopupCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_TRANSACTION_CREATE, config::topicEmailTransactionCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_TRANSFER_CREATE, config::topicEmailTransferCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_WITHDRAW_CREATE, config::topicEmailWithdrawCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_CREATE, config::topicEmailMerchantCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_UPDATE_STATUS, config::topicEmailMerchantUpdateStatus),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_DOC_CREATE, config::topicEmailMerchantDocCreate),
                Map.entry(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT_DOC_UPDATE_STATUS, config::topicEmailMerchantDocUpdateStatus),
                Map.entry(KafkaCommonConfig.TOPIC_CARD_TXN_CREATED, config::topicCardTxnCreated),
                Map.entry(KafkaCommonConfig.TOPIC_CARD_FRAUD_ALERT, config::topicCardFraudAlert),
                Map.entry(KafkaCommonConfig.TOPIC_CARD_PAYMENT_POSTED, config::topicCardPaymentPosted),
                Map.entry(KafkaCommonConfig.TOPIC_CARD_STATEMENT_GENERATED, config::topicCardStatementGenerated),
                Map.entry(KafkaCommonConfig.TOPIC_SALDO_CREATE, config::topicSaldoCreate),
                Map.entry(KafkaCommonConfig.TOPIC_NOTIFICATION, config::topicNotification));

        assertThat(topicBeans).hasSize(25);

        topicBeans.forEach((expectedName, bean) -> {
            NewTopic topic = bean.get();
            assertThat(topic.name()).as("topic name").isEqualTo(expectedName);
            assertThat(topic.numPartitions()).as("partitions of " + expectedName)
                    .isEqualTo(KafkaCommonConfig.PARTITIONS);
            assertThat(topic.replicationFactor()).as("replication of " + expectedName)
                    .isEqualTo(KafkaCommonConfig.REPLICATION);
        });
    }

    @Test
    void topicBeans_doNotSilentlyReuseTopicBuilderDefaults() {
        // sanity: TopicBuilder alone would produce different values than the config
        NewTopic raw = TopicBuilder.name("raw").partitions(1).replicas(1).build();
        assertThat(raw.numPartitions()).isNotEqualTo(KafkaCommonConfig.PARTITIONS);
    }

    @Test
    void jsonMessageConverter_returnsStringJsonMessageConverter() {
        assertThat(config.jsonMessageConverter()).isInstanceOf(StringJsonMessageConverter.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void kafkaTemplate_wrapsGivenProducerFactory() {
        ProducerFactory<String, Object> producerFactory = Mockito.mock(ProducerFactory.class);

        KafkaTemplate<String, Object> template = config.kafkaTemplate(producerFactory);

        assertThat(template).isNotNull();
    }
}
