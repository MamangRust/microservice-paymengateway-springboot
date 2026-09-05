package com.seeder.seeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers one DataSource per payment domain database so seeders can write to each
 * service's own PostgreSQL instance.
 */
@Configuration
public class SeederDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SeederDataSourceConfig.class);

    @Value("${seed.db.identity-url:jdbc:postgresql://user-db:5432/user_service}")
    private String identityUrl;

    @Value("${seed.db.role-url:jdbc:postgresql://role-db:5432/role_service}")
    private String roleUrl;

    @Value("${seed.db.merchant-url:jdbc:postgresql://merchant-db:5432/merchant_service}")
    private String merchantUrl;

    @Value("${seed.db.card-url:jdbc:postgresql://card-db:5432/card_service}")
    private String cardUrl;

    @Value("${seed.db.saldo-url:jdbc:postgresql://saldo-db:5432/saldo_service}")
    private String saldoUrl;

    @Value("${seed.db.topup-url:jdbc:postgresql://topup-db:5432/topup_service}")
    private String topupUrl;

    @Value("${seed.db.transaction-url:jdbc:postgresql://transaction-db:5432/transaction_service}")
    private String transactionUrl;

    @Value("${seed.db.transfer-url:jdbc:postgresql://transfer-db:5432/transfer_service}")
    private String transferUrl;

    @Value("${seed.db.withdraw-url:jdbc:postgresql://withdraw-db:5432/withdraw_service}")
    private String withdrawUrl;

    @Value("${seed.db.username:postgres}")
    private String username;

    @Value("${seed.db.password:password}")
    private String password;

    @Bean
    public Map<String, DataSource> seedDataSources() {
        Map<String, DataSource> map = new HashMap<>();
        map.put("identity", dataSource(identityUrl));
        map.put("role", dataSource(roleUrl));
        map.put("merchant", dataSource(merchantUrl));
        map.put("card", dataSource(cardUrl));
        map.put("saldo", dataSource(saldoUrl));
        map.put("topup", dataSource(topupUrl));
        map.put("transaction", dataSource(transactionUrl));
        map.put("transfer", dataSource(transferUrl));
        map.put("withdraw", dataSource(withdrawUrl));
        log.info("Registered {} seed data sources", map.size());
        return map;
    }

    private DataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }
}