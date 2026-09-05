package com.statsbackfill.statsbackfill.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class BackfillDataSourceConfig {

    @Value("${backfill.order.datasource.url:jdbc:postgresql://order-db:5432/spring-order-service}")
    private String orderUrl;

    @Value("${backfill.transaction.datasource.url:jdbc:postgresql://transaction-db:5432/transaction_service}")
    private String transactionUrl;

    @Value("${backfill.order.datasource.username:postgres}")
    private String username;

    @Value("${backfill.order.datasource.password:password}")
    private String password;

    @Bean
    public DataSource orderDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(orderUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public DataSource transactionDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(transactionUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public JdbcTemplate orderJdbcTemplate() {
        return new JdbcTemplate(orderDataSource());
    }

    @Bean
    public JdbcTemplate transactionJdbcTemplate() {
        return new JdbcTemplate(transactionDataSource());
    }
}