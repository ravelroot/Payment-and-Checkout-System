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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

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
    private final MercadoPagoGateway mercadoPago;
    private final VeloProperties props;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderLifecycle lifecycle, SimulatedGateway simulated,
                          StripeGateway stripe, MercadoPagoGateway mercadoPago, VeloProperties props, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.simulated = simulated;
        this.stripe = stripe;
        this.mercadoPago = mercadoPago;
        this.props = props;
        this.clock = clock;
    }

    public PaymentGateway gateway() {
        return switch (props.payments().provider()) {
            case "STRIPE" -> stripe.configured() ? stripe : simulated;
            case "MERCADO_PAGO" -> mercadoPago.configured() ? mercadoPago : simulated;
            default -> simulated;
        };
    }

    private PaymentGateway gateway(PaymentProvider provider) {
        return switch (provider) {
            case STRIPE -> stripe;
            case MERCADO_PAGO -> mercadoPago;
            case SIMULATED -> simulated;
        };
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
        if (order.getId() != null) {
            orders.lockById(order.getId());
        }
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
        PaymentGateway g = gateway(paid.getProvider());
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

    /**
     * Notificação do Mercado Pago já validada: {@code orderNumber} é o external_reference da preferência e
     * {@code mpPaymentId} o id do pagamento no Mercado Pago.
     */
    @Transactional
    public void mercadoPagoNotified(String orderNumber, String mpPaymentId, String status) {
        Order found = orders.findByNumber(orderNumber).orElse(null);
        if (found == null) {
            return;
        }
        Order order = orders.lockById(found.getId()).orElse(found);
        List<Payment> list = payments.findByOrderIdOrderByIdDesc(order.getId());
        Payment same = list.stream().filter(p -> p.getProvider() == PaymentProvider.MERCADO_PAGO && mpPaymentId.equals(p.getExternalId()))
                .findFirst().orElse(null);
        Payment pending = list.stream()
                .filter(p -> p.getProvider() == PaymentProvider.MERCADO_PAGO && p.getStatus() == PaymentStatus.PENDING)
                .findFirst().orElse(null);
        switch (status) {
            case "approved" -> {
                if (same != null && same.getStatus() != PaymentStatus.PENDING) {
                    return;
                }
                Payment target = same != null ? same : pending;
                if (target == null && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    // O cliente pode tentar de novo no checkout do Mercado Pago depois de uma recusa.
                    target = list.stream()
                            .filter(p -> p.getProvider() == PaymentProvider.MERCADO_PAGO && p.getStatus() == PaymentStatus.FAILED)
                            .findFirst().orElse(null);
                }
                if (target != null && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    target.setExternalId(mpPaymentId);
                    target.setFailureReason(null);
                    confirm(target, order, null);
                } else {
                    lateMercadoPago(order, mpPaymentId);
                }
            }
            case "rejected", "cancelled" -> {
                Payment target = same != null ? same : pending;
                if (target != null && target.getStatus() == PaymentStatus.PENDING) {
                    target.setExternalId(mpPaymentId);
                    target.setStatus(PaymentStatus.FAILED);
                    target.setFailureReason("Pagamento não concluído no Mercado Pago.");
                    payments.save(target);
                }
            }
            case "refunded", "charged_back" -> {
                if (same != null && same.getStatus() == PaymentStatus.PAID) {
                    same.setStatus(PaymentStatus.REFUNDED);
                    same.setRefundedAmount(same.getAmount());
                    payments.save(same);
                    if (order.getStatus().next().contains(OrderStatus.REFUNDED)) {
                        lifecycle.move(order, OrderStatus.REFUNDED, "charged_back".equals(status)
                                ? "Contestação aceita no Mercado Pago" : "Estornado no painel do Mercado Pago", null);
                    } else {
                        log.error("Pedido {} foi estornado no Mercado Pago, mas está {}: confira manualmente.", order.getNumber(),
                                order.getStatus());
                    }
                }
            }
            default -> {
            }
        }
    }

    /**
     * Pagamento aprovado que não tem mais o que confirmar: o pedido já foi cancelado (por exemplo, boleto pago depois do
     * prazo) ou já estava pago por outra tentativa. O valor é registrado e estornado na hora, para não ficar dinheiro
     * cobrado sem pedido.
     */
    private void lateMercadoPago(Order order, String mpPaymentId) {
        Payment late = new Payment();
        late.setOrderId(order.getId());
        late.setProvider(PaymentProvider.MERCADO_PAGO);
        late.setMethod(PaymentMethod.CHECKOUT);
        late.setAmount(Money.of(order.getTotal()));
        late.setInstallments(1);
        late.setExternalId(mpPaymentId);
        late.setStatus(PaymentStatus.PAID);
        late.setPaidAt(LocalDateTime.now(clock));
        late = payments.save(late);
        if (mercadoPago.refund(late, late.getAmount())) {
            late.setStatus(PaymentStatus.REFUNDED);
            late.setRefundedAmount(late.getAmount());
            late.setFailureReason("Pago depois do cancelamento ou em duplicidade: estornado automaticamente.");
            log.warn("Pagamento {} do Mercado Pago chegou para o pedido {} ({}) e foi estornado.", mpPaymentId, order.getNumber(),
                    order.getStatus());
        } else {
            late.setFailureReason("Pago depois do cancelamento ou em duplicidade: estorne no painel do Mercado Pago.");
            log.error("Pagamento {} do Mercado Pago chegou para o pedido {} ({}) e o estorno automático falhou.", mpPaymentId,
                    order.getNumber(), order.getStatus());
        }
        payments.save(late);
    }

    /**
     * Rótulo curto da forma de pagamento de cada pedido (para exportação): usa o pagamento que movimentou dinheiro ou,
     * na falta dele, o mais recente.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> exportLabels(Collection<Long> orderIds) {
        Map<Long, Payment> chosen = new HashMap<>();
        List<Long> ids = List.copyOf(orderIds);
        for (int i = 0; i < ids.size(); i += 500) {
            for (Payment p : payments.findByOrderIdIn(ids.subList(i, Math.min(ids.size(), i + 500)))) {
                Payment current = chosen.get(p.getOrderId());
                if (current == null || rank(p) > rank(current) || (rank(p) == rank(current) && p.getId() > current.getId())) {
                    chosen.put(p.getOrderId(), p);
                }
            }
        }
        Map<Long, String> out = new HashMap<>();
        chosen.forEach((orderId, p) -> out.put(orderId, exportLabel(p)));
        return out;
    }

    private static int rank(Payment p) {
        return p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.REFUNDED ? 1 : 0;
    }

    static String exportLabel(Payment p) {
        String method = switch (p.getMethod()) {
            case CARD -> "Cartão" + (p.getCardBrand() == null ? "" : " " + p.getCardBrand())
                    + (p.getInstallments() > 1 ? " " + p.getInstallments() + "x" : "");
            case PIX -> "PIX";
            case BOLETO -> "Boleto";
            case CHECKOUT -> providerName(p.getProvider());
        };
        String status = switch (p.getStatus()) {
            case PAID -> "";
            case PENDING -> " (pendente)";
            case FAILED -> " (recusado)";
            case CANCELED -> " (cancelado)";
            case REFUNDED -> " (estornado)";
        };
        return method + status;
    }

    public static String providerName(PaymentProvider provider) {
        return switch (provider) {
            case SIMULATED -> "Simulado";
            case STRIPE -> "Stripe";
            case MERCADO_PAGO -> "Mercado Pago";
        };
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
            case CHECKOUT -> "Pago no " + providerName(p.getProvider());
        };
    }
}
