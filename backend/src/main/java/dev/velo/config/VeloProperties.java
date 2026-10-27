package dev.velo.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "velo")
public record VeloProperties(Session session, Payments payments, Shipping shipping, Orders orders, Webhooks webhooks, String publicUrl,
                             boolean seed) {

    public VeloProperties {
        session = session == null ? new Session(20, 30) : session;
        payments = payments == null ? new Payments("SIMULATED", 10, null, null) : payments;
        shipping = shipping == null ? new Shipping(new BigDecimal("19.90"), new BigDecimal("299.00")) : shipping;
        orders = orders == null ? new Orders(60) : orders;
        webhooks = webhooks == null ? new Webhooks(null, null) : webhooks;
        publicUrl = publicUrl == null || publicUrl.isBlank() ? "http://localhost:8080" : publicUrl.replaceAll("/+$", "");
    }

    public record Session(int idleMinutes, int maxMinutes) {
    }

    public record Payments(String provider, int maxInstallments, Stripe stripe, MercadoPago mercadoPago) {

        public Payments {
            provider = normalizeProvider(provider);
            maxInstallments = maxInstallments <= 0 ? 10 : maxInstallments;
            stripe = stripe == null ? new Stripe(null, null) : stripe;
            mercadoPago = mercadoPago == null ? new MercadoPago(null, null) : mercadoPago;
        }

        static String normalizeProvider(String value) {
            if (value == null || value.isBlank()) {
                return "SIMULATED";
            }
            String v = value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
            return "MERCADOPAGO".equals(v) ? "MERCADO_PAGO" : v;
        }
    }

    public record Stripe(String secretKey, String webhookSecret) {

        public boolean configured() {
            return secretKey != null && !secretKey.isBlank();
        }

        public boolean webhookSecretConfigured() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }
    }

    public record MercadoPago(String accessToken, String webhookSecret) {

        public boolean configured() {
            return accessToken != null && !accessToken.isBlank();
        }

        public boolean webhookSecretConfigured() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }
    }

    public record Shipping(BigDecimal flatRate, BigDecimal freeAbove) {
    }

    public record Orders(int paymentWindowMinutes) {
    }

    public record Webhooks(String url, String secret) {

        public Webhooks {
            url = url == null || url.isBlank() ? null : url.trim();
            secret = secret == null || secret.isBlank() ? null : secret;
        }

        public boolean configured() {
            return url != null;
        }

        public boolean signed() {
            return secret != null;
        }
    }
}
