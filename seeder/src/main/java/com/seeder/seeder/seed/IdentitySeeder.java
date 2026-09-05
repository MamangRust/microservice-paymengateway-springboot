package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeder untuk domain identity (user-db). E-commerce user menggunakan UUID id.
 * Idempotent via ON CONFLICT (username).
 */
@Component
public class IdentitySeeder implements Seeder {

    @Override
    public String domain() {
        return "identity";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("identity");
        String adminPw = ctx.passwordUtil().hashPassword("admin123");
        String userPw = ctx.passwordUtil().hashPassword("user123");

        jdbc.update("""
            INSERT INTO users (id, username, password, email, role) VALUES
            (?, 'admin', ?, 'admin@shop.local', 'ADMIN'),
            (?, 'user', ?, 'user@shop.local', 'USER')
            ON CONFLICT (username) DO NOTHING
            """, UUID.randomUUID(), adminPw, UUID.randomUUID(), userPw);
        ctx.log().info("Seeded identity users (idempotent)");
    }
}