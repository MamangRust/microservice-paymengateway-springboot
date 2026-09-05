package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain transfer (transfer-db). Idempotent via ON CONFLICT (idempotency_key).
 */
@Component
public class TransferSeeder implements Seeder {

    @Override
    public String domain() { return "transfer"; }

    @Override
    public int order() { return 40; }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("transfer");
        jdbc.update("""
            INSERT INTO transfers (transfer_no, transfer_from, transfer_to, transfer_amount, status, idempotency_key) VALUES
            ('00000000-0000-0000-0000-000000000001', '4111111111111111', '5500000000000004', 100000, 'SUCCESS', 'seed-transfer-001')
            ON CONFLICT (idempotency_key) DO NOTHING
            """);
        ctx.log().info("Seeded transfers (idempotent)");
    }
}