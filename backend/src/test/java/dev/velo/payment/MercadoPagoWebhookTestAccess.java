package dev.velo.payment;

public final class MercadoPagoWebhookTestAccess {

    private MercadoPagoWebhookTestAccess() {
    }

    public static boolean verify(String dataId, String requestId, String header, String secret, long now) {
        return MercadoPagoWebhookController.verify(dataId, requestId, header, secret, now);
    }

    public static String manifest(String dataId, String requestId, String ts) {
        return MercadoPagoWebhookController.manifest(dataId, requestId, ts);
    }
}
