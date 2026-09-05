package com.common.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for the {@link SeedContext} record. Constructing a JdbcTemplate
 * does not obtain a connection, so the happy path is network-free.
 */
class SeedContextTest {

    private final Logger log = LoggerFactory.getLogger("test");
    private final PasswordUtil passwordUtil = new PasswordUtil();

    @Test
    void record_exposesConstructorComponents() {
        DataSource userDs = mock(DataSource.class);
        SeedContext ctx = new SeedContext(Map.of("user", userDs), log, passwordUtil);

        assertThat(ctx.dataSources()).containsEntry("user", userDs);
        assertThat(ctx.log()).isSameAs(log);
        assertThat(ctx.passwordUtil()).isSameAs(passwordUtil);
    }

    @Test
    void jdbc_unknownDomain_throwsIllegalStateWithExactMessage() {
        SeedContext ctx = new SeedContext(Map.of(), log, passwordUtil);

        assertThatThrownBy(() -> ctx.jdbc("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No data source registered for domain: missing");
    }

    @Test
    void jdbc_knownDomain_returnsJdbcTemplateBoundToThatDataSource() {
        DataSource userDs = mock(DataSource.class);
        SeedContext ctx = new SeedContext(Map.of("user", userDs), log, passwordUtil);

        JdbcTemplate template = ctx.jdbc("user");

        assertThat(template).isInstanceOf(JdbcTemplate.class);
        assertThat(template.getDataSource()).isSameAs(userDs);
    }
}
