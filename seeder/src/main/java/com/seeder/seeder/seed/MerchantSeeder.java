package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain merchant (merchant-db). Idempotent via ON CONFLICT (name).
 */
@Component
public class MerchantSeeder implements Seeder {

    @Override
    public String domain() {
        return "merchant";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("merchant");
        jdbc.update("""
            INSERT INTO merchants (user_id, name, description, address, contact_email, contact_phone, status) VALUES
            (1, 'Toko Elektronik Jaya', 'Toko elektronik lengkap', 'Jl. Merdeka 1, Jakarta', 'jaya@shop.local', '081234567890', 'SUCCESS'),
            (2, 'Fashion Kita', 'Toko fashion modern', 'Jl. Sudirman 12, Bandung', 'fashion@shop.local', '081298765432', 'SUCCESS')
            ON CONFLICT (name) DO NOTHING
            """);
        ctx.log().info("Seeded merchants (idempotent)");
    }
}