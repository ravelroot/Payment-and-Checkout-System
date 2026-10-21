package dev.velo.payment;

import dev.velo.common.ApiException;
import dev.velo.common.Money;
import dev.velo.common.Times;
import dev.velo.config.VeloProperties;
import dev.velo.order.Order;
import dev.velo.order.OrderDtos;
import dev.velo.order.OrderLifecycle;
import dev.velo.order.OrderRepository;
import dev.velo.order.OrderStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    public record TestCard(String number, String result) {
    }

    public record Options(PaymentProvider provider, List<PaymentMethod> methods, int maxInstallments, boolean simulated,
                          List<TestCard> testCards) {
    }

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderLifecycle lifecycle;
    private final SimulatedGateway simulated;
    private final StripeGateway stripe;
    private final VeloProperties props;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderLifecycle lifecycle, SimulatedGateway simulated,
                          StripeGateway stripe, VeloProperties props, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.simulated = simulated;
        this.stripe = stripe;
        this.props = props;
        this.clock = clock;
    }

    public PaymentGateway gateway() {
        return "STRIPE".equals(props.payments().provider()) && stripe.configured() ? stripe : simulated;
    }

    public Options options() {
        PaymentGateway g = gateway();
        boolean sim = g.provider() == PaymentProvider.SIMULATED;
        List<PaymentMethod> methods = sim ? List.of(PaymentMethod.CARD, PaymentMethod.PIX, PaymentMethod.BOLETO)
                : List.of(PaymentMethod.CHECKOUT);
        List<TestCard> cards = sim ? List.of(
                new TestCard("4242 4242 4242 4242", "Aprovado"),
                new TestCard("5555 5555 5555 4444", "Aprovado"),
                new TestCard("4000 0000 0000 0002", "Recusado pelo emissor"),
                new TestCard("4000 0000 0000 9995", "Saldo insuficiente")) : List.of();
        return new Options(g.provider(), methods, props.payments().maxInstallments(), sim, cards);
    }

    @Transactional
    public Payment pay(Order order, PaymentRequest request) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("NOT_PAYABLE", "Este pedido não está aguardando pagamento.");
        }
        PaymentGateway g = gateway();
        if (!g.supports(request.method())) {
            throw ApiException.badRequest("METHOD", "Forma de pagamento indisponível.");
        }
        int installments = request.method() == PaymentMethod.CARD && request.installments() != null
                ? Math.max(1, Math.min(request.installments(), props.payments().maxInstallments())) : 1;
        for (Payment old : payments.findByOrderIdOrderByIdDesc(order.getId())) {
            if (old.getStatus() == PaymentStatus.PENDING) {
                old.setStatus(PaymentStatus.CANCELED);
                payments.save(old);
            }
        }
        GatewayResult r = g.start(order, request);
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setProvider(g.provider());
        p.setMethod(request.method());
        p.setAmount(Money.of(order.getTotal()));
        p.setInstallments(installments);
        p.setStatus(r.status());
        p.setExternalId(r.externalId());
        p.setCheckoutUrl(r.checkoutUrl());
        p.setCardBrand(r.cardBrand());
        p.setCardLast4(r.cardLast4());
        p.setPixCode(r.pixCode());
        p.setBoletoLine(r.boletoLine());
        p.setFailureReason(r.failureReason());
        p.setExpiresAt(r.expiresAt());
        if (r.status() == PaymentStatus.PAID) {
            p.setPaidAt(LocalDateTime.now(clock));
        }
        Payment saved = payments.save(p);
        if (saved.getStatus() == PaymentStatus.PAID) {
            lifecycle.move(order, OrderStatus.PAID, label(saved), null);
        }
        return saved;
    }

    @Transactional
    public Payment simulate(Long paymentId, boolean approve, Long actorId, boolean backoffice) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Pagamento não encontrado."));
        Order order = orders.findById(p.getOrderId()).orElseThrow();
        if (!backoffice && !order.getUserId().equals(actorId)) {
            throw ApiException.notFound("Pagamento não encontrado.");
        }
        if (p.getProvider() != PaymentProvider.SIMULATED) {
            throw ApiException.badRequest("NOT_SIMULATED", "Só pagamentos simulados podem ser confirmados manualmente.");
        }
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict("NOT_PENDING", "Este pagamento não está pendente.");
        }
        if (approve) {
            confirm(p, order, actorId);
        } else {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason("Pagamento não concluído.");
            payments.save(p);
        }
        return p;
    }

    @Transactional
    public void confirm(Payment p, Order order, Long actorId) {
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now(clock));
        payments.save(p);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            lifecycle.move(order, OrderStatus.PAID, label(p), actorId);
        }
    }

    @Transactional
    public void cancelPending(Order order) {
        for (Payment p : payments.findByOrderIdOrderByIdDesc(order.getId())) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.CANCELED);
                payments.save(p);
            }
        }
    }

    @Transactional
    public void refund(Order order) {
        Payment paid = payments.findByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID).findFirst().orElse(null);
        if (paid == null) {
            return;
        }
        PaymentGateway g = paid.getProvider() == PaymentProvider.STRIPE ? stripe : simulated;
        if (!g.refund(paid, paid.getAmount())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                    "O provedor de pagamento não confirmou o estorno. Tente de novo em instantes.");
        }
        paid.setStatus(PaymentStatus.REFUNDED);
        paid.setRefundedAmount(paid.getAmount());
        payments.save(paid);
    }

    @Transactional
    public void stripeCompleted(String sessionId, boolean paid) {
        payments.findFirstByProviderAndExternalId(PaymentProvider.STRIPE, sessionId).ifPresent(p -> {
            Order order = orders.findById(p.getOrderId()).orElseThrow();
            if (paid && p.getStatus() == PaymentStatus.PENDING) {
                confirm(p, order, null);
            } else if (!paid && p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureReason("Pagamento não concluído no Stripe.");
                payments.save(p);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.PaymentView> views(Long orderId) {
        return payments.findByOrderIdOrderByIdDesc(orderId).stream().map(PaymentService::view).toList();
    }

    public static OrderDtos.PaymentView view(Payment p) {
        return new OrderDtos.PaymentView(p.getId(), p.getProvider(), p.getMethod(), p.getStatus(), p.getAmount(), p.getInstallments(),
                p.getCardBrand(), p.getCardLast4(), p.getPixCode(), p.getBoletoLine(), p.getCheckoutUrl(), p.getFailureReason(),
                p.getRefundedAmount(), Times.instant(p.getPaidAt()), Times.instant(p.getExpiresAt()), Times.instant(p.getCreatedAt()));
    }

    private static String label(Payment p) {
        return switch (p.getMethod()) {
            case CARD -> "Pago no cartão " + (p.getCardBrand() == null ? "" : p.getCardBrand() + " ") + "final " + p.getCardLast4()
                    + (p.getInstallments() > 1 ? " em " + p.getInstallments() + "x" : "");
            case PIX -> "Pago via PIX";
            case BOLETO -> "Boleto compensado";
            case CHECKOUT -> "Pago no Stripe";
        };
    }
}
