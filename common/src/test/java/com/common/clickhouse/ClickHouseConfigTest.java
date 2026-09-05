package com.common.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import com.clickhouse.jdbc.ClickHouseDataSource;

import javax.sql.DataSource;

/**
 * Direct unit test of {@link ClickHouseConfig#clickhouseDataSource()}.
 *
 * The ClickHouseDataSource constructor only parses the URL and stores
 * properties — no connection is opened until getConnection() is called —
 * so constructing the bean is network-free and safe to assert here.
 * getConnection() itself would need a live server and is NOT tested.
 *
 * Quirk kept on purpose: this project's default URL points at the
 * {@code pos_stats} database (copy-paste from the POS project), while the
 * ecommerce stats databases are separate. We assert what the code does,
 * not what it should do.
 */
class ClickHouseConfigTest {

    private ClickHouseConfig config;

    @BeforeEach
    void setUp() {
        config = new ClickHouseConfig();
        ReflectionTestUtils.setField(config, "url", "jdbc:clickhouse://localhost:8123/pos_stats");
        ReflectionTestUtils.setField(config, "username", "default");
        ReflectionTestUtils.setField(config, "password", "");
    }

    @Test
    void defaultUrlAnnotation_pointsAtLocalPosStatsDatabase() {
        // asserts the @Value default — what the code ships with, quirk included
        assertThat(defaultUrl()).isEqualTo("${clickhouse.url:jdbc:clickhouse://localhost:8123/pos_stats}");
    }

    @Test
    void clickhouseDataSource_returnsClickHouseDataSourceWithoutNetwork() {
        DataSource dataSource = config.clickhouseDataSource();

        assertThat(dataSource).isInstanceOf(ClickHouseDataSource.class);
    }

    @Test
    void clickhouseDataSource_parsesDefaultLocalhostPosStatsUrl() {
        ReflectionTestUtils.setField(config, "url", "jdbc:clickhouse://localhost:8123/pos_stats");

        // construction parses the URL only; no connection is opened
        assertThat(config.clickhouseDataSource()).isNotNull();
    }

    @Test
    void clickhouseDataSource_wrapsSqlExceptionInRuntime() {
        ReflectionTestUtils.setField(config, "url", "jdbc:clickhouse://");

        assertThatThrownBy(config::clickhouseDataSource)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create ClickHouse datasource");
    }

    private String defaultUrl() {
        try {
            Field url = ClickHouseConfig.class.getDeclaredField("url");
            Value annotation = url.getAnnotation(Value.class);
            assertThat(annotation).as("@Value on url field").isNotNull();
            return annotation.value();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
