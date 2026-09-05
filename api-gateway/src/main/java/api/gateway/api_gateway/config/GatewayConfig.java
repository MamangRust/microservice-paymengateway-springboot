package api.gateway.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import api.gateway.api_gateway.filter.JwtAuthFilter;

@Configuration
public class GatewayConfig {
    private final JwtAuthFilter jwtAuthFilter;

    public GatewayConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://auth-service"))
            .route("user", r -> r.path("/users/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://user"))
            .route("role", r -> r.path("/roles/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://role-service"))
            .route("merchant", r -> r.path("/merchants/**", "/merchant-documents/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://merchant-service"))
            .route("card", r -> r.path("/cards/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://card-service"))
            .route("saldo", r -> r.path("/saldos/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://saldo-service"))
            .route("topup", r -> r.path("/topups/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://topup-service"))
            .route("transaction", r -> r.path("/transactions/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://transaction-service"))
            .route("transfer", r -> r.path("/transfers/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://transfer-service"))
            .route("withdraw", r -> r.path("/withdraws/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://withdraw-service"))
            .route("stats", r -> r.path("/stats/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://stats-reader"))
            .build();
    }
}
