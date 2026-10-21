package dev.velo.payment;

public final class StripeWebhookTestAccess {

    private StripeWebhookTestAccess() {
    }

    public static boolean verify(String payload, String header, String secret, long now) {
        return StripeWebhookController.verify(payload, header, secret, now);
    }
}
