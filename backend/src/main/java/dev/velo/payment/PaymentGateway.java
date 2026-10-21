package dev.velo.payment;

import dev.velo.order.Order;
import java.math.BigDecimal;

public interface PaymentGateway {

    PaymentProvider provider();

    boolean supports(PaymentMethod method);

    GatewayResult start(Order order, PaymentRequest request);

    boolean refund(Payment payment, BigDecimal amount);
}
