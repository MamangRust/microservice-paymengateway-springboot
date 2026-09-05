package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain saldo (saldo-db). Idempotent via ON CONFLICT (card_number).
 */
@Component
public class SaldoSeeder implements Seeder {

    @Override
    public String domain() { return "saldo"; }

    @Override
    public int order() { return 30; }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("saldo");
        jdbc.update("""
            INSERT INTO saldos (card_number, total_balance) VALUES
            ('4111111111111111', 1000000),
            ('5500000000000004', 500000)
            ON CONFLICT (card_number) DO NOTHING
            """);
        ctx.log().info("Seeded saldos (idempotent)");
    }
}