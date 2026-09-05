package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain withdraw (withdraw-db). Idempotent via ON CONFLICT (idempotency_key).
 */
@Component
public class WithdrawSeeder implements Seeder {

    @Override
    public String domain() { return "withdraw"; }

    @Override
    public int order() { return 40; }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("withdraw");
        jdbc.update("""
            INSERT INTO withdraws (withdraw_no, card_number, withdraw_amount, status, idempotency_key) VALUES
            ('00000000-0000-0000-0000-000000000001', '4111111111111111', 200000, 'SUCCESS', 'seed-withdraw-001')
            ON CONFLICT (idempotency_key) DO NOTHING
            """);
        ctx.log().info("Seeded withdraws (idempotent)");
    }
}