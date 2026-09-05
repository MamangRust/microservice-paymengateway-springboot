package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain card (card-db). Idempotent via ON CONFLICT (card_number).
 */
@Component
public class CardSeeder implements Seeder {

    @Override
    public String domain() { return "card"; }

    @Override
    public int order() { return 25; }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("card");
        jdbc.update("""
            INSERT INTO cards (user_id, card_number, card_type, expire_date, cvv, card_provider, status, credit_limit, points) VALUES
            (1, '4111111111111111', 'CREDIT', '2028-12-31', '123', 'VISA', 'ACTIVE', 10000000, 1000),
            (2, '5500000000000004', 'DEBIT', '2027-06-30', '456', 'MASTERCARD', 'ACTIVE', 5000000, 500)
            ON CONFLICT (card_number) DO NOTHING
            """);
        ctx.log().info("Seeded cards (idempotent)");
    }
}