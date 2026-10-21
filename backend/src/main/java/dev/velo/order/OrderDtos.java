package dev.velo.order;

import dev.velo.payment.PaymentMethod;
import dev.velo.payment.PaymentProvider;
import dev.velo.payment.PaymentStatus;
import dev.velo.user.AccountDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(Long addressId, @Valid AccountDtos.AddressInput address, @Size(max = 30) String couponCode,
                                  @Size(max = 500) String notes) {
    }

    public record Summary(Long id, String number, OrderStatus status, String statusLabel, BigDecimal total, int items,
                          Instant placedAt, Long customerId, String customerName, String customerEmail) {
    }

    public record Item(Long productId, String sku, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                       String imageUrl, String slug) {
    }

    public record History(OrderStatus from, OrderStatus to, String toLabel, String note, String by, Instant at) {
    }

    public record PaymentView(Long id, PaymentProvider provider, PaymentMethod method, PaymentStatus status, BigDecimal amount,
                              int installments, String cardBrand, String cardLast4, String pixCode, String boletoLine,
                              String checkoutUrl, String failureReason, BigDecimal refundedAmount, Instant paidAt,
                              Instant expiresAt, Instant createdAt) {
    }

    public record Customer(Long id, String name, String email, String phone) {
    }

    public record Detail(Long id, String number, OrderStatus status, String statusLabel, BigDecimal subtotal, BigDecimal discount,
                         BigDecimal shipping, BigDecimal total, String couponCode, String recipient, String zipCode, String street,
                         String streetNumber, String complement, String district, String city, String state,
                         String trackingCode, String notes, Instant placedAt, Instant paidAt, Instant shippedAt,
                         Instant deliveredAt, Instant canceledAt, Instant paymentDeadline, List<Item> items,
                         List<History> history, List<PaymentView> payments, Customer customer, boolean canPay,
                         boolean canCancel, List<OrderStatus> nextStatuses) {
    }

    public record StatusUpdate(@NotNull OrderStatus status, @Size(max = 300) String note, @Size(max = 40) String trackingCode) {
    }
}
