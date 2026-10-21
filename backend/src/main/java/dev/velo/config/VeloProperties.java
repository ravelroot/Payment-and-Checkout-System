package dev.velo.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "velo")
public record VeloProperties(Session session, Payments payments, Shipping shipping, Orders orders, String publicUrl, boolean seed) {

    public VeloProperties {
        session = session == null ? new Session(20, 30) : session;
        payments = payments == null ? new Payments("SIMULATED", 10, null) : payments;
        shipping = shipping == null ? new Shipping(new BigDecimal("19.90"), new BigDecimal("299.00")) : shipping;
        orders = orders == null ? new Orders(60) : orders;
        publicUrl = publicUrl == null || publicUrl.isBlank() ? "http://localhost:8080" : publicUrl.replaceAll("/+$", "");
    }

    public record Session(int idleMinutes, int maxMinutes) {
    }

    public record Payments(String provider, int maxInstallments, Stripe stripe) {

        public Payments {
            provider = provider == null || provider.isBlank() ? "SIMULATED" : provider.trim().toUpperCase();
            maxInstallments = maxInstallments <= 0 ? 10 : maxInstallments;
            stripe = stripe == null ? new Stripe(null, null) : stripe;
        }
    }

    public record Stripe(String secretKey, String webhookSecret) {

        public boolean configured() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    public record Shipping(BigDecimal flatRate, BigDecimal freeAbove) {
    }

    public record Orders(int paymentWindowMinutes) {
    }
}
