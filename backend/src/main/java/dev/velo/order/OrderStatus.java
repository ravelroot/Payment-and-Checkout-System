package dev.velo.order;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELED, REFUNDED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELED);
            case PAID -> EnumSet.of(PROCESSING, CANCELED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(REFUNDED);
            case CANCELED, REFUNDED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean isPaid() {
        return this == PAID || this == PROCESSING || this == SHIPPED || this == DELIVERED;
    }
}
