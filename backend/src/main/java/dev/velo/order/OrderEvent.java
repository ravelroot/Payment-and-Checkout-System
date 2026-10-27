package dev.velo.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de domínio publicado quando um pedido é criado ou muda de situação. É um retrato do pedido no momento da
 * publicação; os ouvintes (como os webhooks de saída) só o recebem depois que a transação for confirmada.
 */
public record OrderEvent(String type, Long orderId, String number, OrderStatus status, BigDecimal total, String trackingCode,
                         Long userId, Instant at) {

    public static final String CREATED = "order.created";

    static OrderEvent of(String type, Order o, Instant at) {
        return new OrderEvent(type, o.getId(), o.getNumber(), o.getStatus(), o.getTotal(), o.getTrackingCode(), o.getUserId(), at);
    }

    public static String type(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> CREATED;
            case PAID -> "order.paid";
            case PROCESSING -> "order.processing";
            case SHIPPED -> "order.shipped";
            case DELIVERED -> "order.delivered";
            case CANCELED -> "order.canceled";
            case REFUNDED -> "order.refunded";
        };
    }
}
