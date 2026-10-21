package dev.velo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.velo.cart.CartService;
import dev.velo.catalog.Product;
import dev.velo.catalog.ProductRepository;
import dev.velo.common.ApiException;
import dev.velo.coupon.CouponRepository;
import dev.velo.order.Order;
import dev.velo.order.OrderDtos;
import dev.velo.order.OrderService;
import dev.velo.order.OrderStatus;
import dev.velo.payment.CardInput;
import dev.velo.payment.Payment;
import dev.velo.payment.PaymentMethod;
import dev.velo.payment.PaymentRequest;
import dev.velo.payment.PaymentService;
import dev.velo.payment.PaymentStatus;
import dev.velo.seed.DataSeeder;
import dev.velo.user.AccountDtos;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@VeloTest
class CheckoutFlowTest {

    @Autowired
    CartService carts;

    @Autowired
    OrderService orders;

    @Autowired
    PaymentService payments;

    @Autowired
    ProductRepository products;

    @Autowired
    UserRepository users;

    @Autowired
    CouponRepository coupons;

    Long customer;
    Product mug;

    @BeforeEach
    void setUp() {
        User u = users.findByEmail(DataSeeder.CUSTOMER_EMAIL).orElseThrow();
        customer = u.getId();
        carts.clear(customer);
        mug = products.findBySlug("caneca-de-ceramica-fosca").orElseThrow();
    }

    private Order placeOrder(int quantity, String coupon) {
        carts.add(customer, mug.getId(), quantity);
        AccountDtos.AddressInput address = new AccountDtos.AddressInput("Casa", "Clara Cliente", "22240003", "Rua Teste", "10",
                null, "Centro", "Rio de Janeiro", "rj", false);
        return orders.checkout(customer, new OrderDtos.CheckoutRequest(null, address, coupon, null));
    }

    @Test
    void cardPaymentConfirmsTheOrderAndTakesStock() {
        int before = products.findById(mug.getId()).orElseThrow().getStock();
        Order o = placeOrder(2, null);
        assertThat(o.getNumber()).startsWith("VL");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(o.getShipZipCode()).isEqualTo("22240-003");
        assertThat(o.getShipState()).isEqualTo("RJ");
        assertThat(products.findById(mug.getId()).orElseThrow().getStock()).isEqualTo(before - 2);
        assertThat(carts.lines(customer)).isEmpty();

        Payment p = payments.pay(o, new PaymentRequest(PaymentMethod.CARD, 3,
                new CardInput("CLARA CLIENTE", "4242 4242 4242 4242", "12/40", "123")));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getCardLast4()).isEqualTo("4242");
        assertThat(p.getCardBrand()).isEqualTo("Visa");
        assertThat(orders.byNumber(o.getNumber()).getStatus()).isEqualTo(OrderStatus.PAID);

        orders.cancelByCustomer(o.getNumber(), customer);
        assertThat(orders.byNumber(o.getNumber()).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(payments.views(o.getId()).get(0).status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(products.findById(mug.getId()).orElseThrow().getStock()).isEqualTo(before);
    }

    @Test
    void declinedCardKeepsTheOrderWaiting() {
        Order o = placeOrder(1, null);
        Payment p = payments.pay(o, new PaymentRequest(PaymentMethod.CARD, 1,
                new CardInput("CLARA CLIENTE", "4000 0000 0000 0002", "12/40", "123")));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureReason()).contains("recusado");
        assertThat(orders.byNumber(o.getNumber()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void pixIsConfirmedLater() {
        Order o = placeOrder(1, null);
        Payment p = payments.pay(o, new PaymentRequest(PaymentMethod.PIX, null, null));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getPixCode()).startsWith("000201");
        payments.simulate(p.getId(), true, customer, false);
        assertThat(orders.byNumber(o.getNumber()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void couponDiscountIsAppliedAndCounted() {
        int used = coupons.findByCode("BEMVINDO10").orElseThrow().getUsedCount();
        Order o = placeOrder(3, "bemvindo10");
        BigDecimal expected = o.getSubtotal().multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(o.getDiscount()).isEqualByComparingTo(expected);
        assertThat(o.getTotal()).isEqualByComparingTo(o.getSubtotal().subtract(o.getDiscount()).add(o.getShippingCost()));
        assertThat(coupons.findByCode("BEMVINDO10").orElseThrow().getUsedCount()).isEqualTo(used + 1);
        orders.cancelByCustomer(o.getNumber(), customer);
        assertThat(coupons.findByCode("BEMVINDO10").orElseThrow().getUsedCount()).isEqualTo(used);
    }

    @Test
    void cannotBuyMoreThanTheStock() {
        Product p = products.findBySlug("toca-discos-compacto").orElseThrow();
        assertThatThrownBy(() -> carts.add(customer, p.getId(), p.getStock() + 1)).isInstanceOf(ApiException.class);
    }

    @Test
    void shippingNeedsTrackingCode() {
        Order o = placeOrder(1, null);
        payments.pay(o, new PaymentRequest(PaymentMethod.CARD, 1, new CardInput("CLARA", "5555555555554444", "1240", "123")));
        orders.changeStatus(o.getNumber(), new OrderDtos.StatusUpdate(OrderStatus.PROCESSING, null, null), null);
        assertThatThrownBy(() -> orders.changeStatus(o.getNumber(), new OrderDtos.StatusUpdate(OrderStatus.SHIPPED, null, null), null))
                .isInstanceOf(ApiException.class);
        Order shipped = orders.changeStatus(o.getNumber(), new OrderDtos.StatusUpdate(OrderStatus.SHIPPED, null, "br123"), null);
        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.getTrackingCode()).isEqualTo("BR123");
    }
}
