package dev.velo.payment;

import java.time.LocalDateTime;

public record GatewayResult(PaymentStatus status, String externalId, String checkoutUrl, String cardBrand, String cardLast4,
                            String pixCode, String boletoLine, String failureReason, LocalDateTime expiresAt) {

    public static GatewayResult failed(String reason) {
        return new GatewayResult(PaymentStatus.FAILED, null, null, null, null, null, null, reason, null);
    }
}
