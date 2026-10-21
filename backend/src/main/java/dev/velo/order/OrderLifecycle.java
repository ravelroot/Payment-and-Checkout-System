package dev.velo.order;

import dev.velo.catalog.CatalogService;
import dev.velo.common.ApiException;
import dev.velo.coupon.CouponService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderLifecycle {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final CatalogService catalog;
    private final CouponService coupons;
    private final Clock clock;

    public OrderLifecycle(OrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository history,
                          CatalogService catalog, CouponService coupons, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.catalog = catalog;
        this.coupons = coupons;
        this.clock = clock;
    }

    @Transactional
    public Order move(Order order, OrderStatus to, String note, Long userId) {
        OrderStatus from = order.getStatus();
        if (from == to) {
            return order;
        }
        if (!from.next().contains(to)) {
            throw ApiException.conflict("INVALID_STATUS", "Não é possível passar de " + label(from) + " para " + label(to) + ".");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        switch (to) {
            case PAID -> order.setPaidAt(now);
            case SHIPPED -> {
                if (order.getTrackingCode() == null || order.getTrackingCode().isBlank()) {
                    throw ApiException.badRequest("TRACKING", "Informe o código de rastreio para marcar como enviado.");
                }
                order.setShippedAt(now);
            }
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELED, REFUNDED -> {
                order.setCanceledAt(now);
                for (OrderItem i : items.findByOrderIdOrderByIdAsc(order.getId())) {
                    if (i.getProductId() != null) {
                        catalog.restock(i.getProductId(), i.getQuantity(), order.getId(), userId,
                                to == OrderStatus.CANCELED ? "Pedido cancelado" : "Pedido estornado");
                    }
                }
                if (from == OrderStatus.PENDING_PAYMENT) {
                    coupons.release(order.getCouponCode());
                }
            }
            default -> {
            }
        }
        order.setStatus(to);
        Order saved = orders.save(order);
        log(saved, from, to, note, userId, now);
        return saved;
    }

    void log(Order order, OrderStatus from, OrderStatus to, String note, Long userId, LocalDateTime when) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(note);
        h.setUserId(userId);
        h.setCreatedAt(when);
        history.save(h);
    }

    public static String label(OrderStatus s) {
        return switch (s) {
            case PENDING_PAYMENT -> "Aguardando pagamento";
            case PAID -> "Pago";
            case PROCESSING -> "Em separação";
            case SHIPPED -> "Enviado";
            case DELIVERED -> "Entregue";
            case CANCELED -> "Cancelado";
            case REFUNDED -> "Estornado";
        };
    }
}
