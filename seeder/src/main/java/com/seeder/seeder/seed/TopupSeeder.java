package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain topup (topup-db). Idempotent via ON CONFLICT (idempotency_key).
 */
@Component
public class TopupSeeder implements Seeder {

    @Override
    public String domain() { return "topup"; }

    @Override
    public int order() { return 40; }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("topup");
        jdbc.update("""
            INSERT INTO topups (topup_no, card_number, topup_amount, topup_method, status, idempotency_key) VALUES
            ('00000000-0000-0000-0000-000000000001', '4111111111111111', 500000, 'BANK_TRANSFER', 'SUCCESS', 'seed-topup-001')
            ON CONFLICT (idempotency_key) DO NOTHING
            """);
        ctx.log().info("Seeded topups (idempotent)");
    }
}