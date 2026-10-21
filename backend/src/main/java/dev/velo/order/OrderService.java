package dev.velo.order;

import dev.velo.cart.CartItem;
import dev.velo.cart.CartService;
import dev.velo.catalog.CatalogService;
import dev.velo.catalog.Product;
import dev.velo.catalog.ProductRepository;
import dev.velo.common.ApiException;
import dev.velo.common.Money;
import dev.velo.common.PageResponse;
import dev.velo.common.Texts;
import dev.velo.common.Times;
import dev.velo.config.VeloProperties;
import dev.velo.coupon.CouponService;
import dev.velo.payment.PaymentService;
import dev.velo.user.AccountDtos;
import dev.velo.user.Address;
import dev.velo.user.AddressRepository;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public record Filter(Long userId, OrderStatus status, String q, LocalDate from, LocalDate to, int page, int size) {
    }

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final OrderLifecycle lifecycle;
    private final CartService carts;
    private final CatalogService catalog;
    private final ProductRepository products;
    private final CouponService coupons;
    private final PaymentService payments;
    private final AddressRepository addresses;
    private final UserRepository users;
    private final Pricing pricing;
    private final EntityManager em;
    private final VeloProperties props;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository history,
                        OrderLifecycle lifecycle, CartService carts, CatalogService catalog, ProductRepository products,
                        CouponService coupons, PaymentService payments, AddressRepository addresses, UserRepository users,
                        Pricing pricing, EntityManager em, VeloProperties props, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.lifecycle = lifecycle;
        this.carts = carts;
        this.catalog = catalog;
        this.products = products;
        this.coupons = coupons;
        this.payments = payments;
        this.addresses = addresses;
        this.users = users;
        this.pricing = pricing;
        this.em = em;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Order checkout(Long userId, OrderDtos.CheckoutRequest req) {
        List<CartItem> lines = carts.lines(userId);
        if (lines.isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "Seu carrinho está vazio.");
        }
        Order o = new Order();
        o.setUserId(userId);
        fillAddress(o, userId, req);
        BigDecimal subtotal = Money.ZERO;
        int count = 0;
        for (CartItem line : lines) {
            Product p = line.getProduct();
            subtotal = subtotal.add(Money.of(p.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()))));
            count += line.getQuantity();
        }
        BigDecimal discount = Money.ZERO;
        String couponCode = CouponService.normalize(req.couponCode());
        CouponService.Applied applied = null;
        if (couponCode != null) {
            applied = coupons.evaluate(couponCode, subtotal).orElseThrow();
            if (applied.coupon() == null) {
                throw ApiException.badRequest("COUPON", applied.message());
            }
            discount = applied.discount();
        }
        BigDecimal shipping = pricing.shipping(subtotal, count);
        LocalDateTime now = LocalDateTime.now(clock);
        o.setSubtotal(Money.of(subtotal));
        o.setDiscount(discount);
        o.setShippingCost(shipping);
        o.setTotal(Money.of(subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO)));
        o.setCouponCode(applied == null ? null : applied.coupon().getCode());
        o.setNotes(Texts.clean(req.notes()));
        o.setPlacedAt(now);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setNumber("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        o = orders.save(o);
        o.setNumber(number(o.getId()));
        o = orders.save(o);
        for (CartItem line : lines) {
            Product p = line.getProduct();
            catalog.reserve(p.getId(), line.getQuantity(), o.getId(), userId);
            OrderItem i = new OrderItem();
            i.setOrderId(o.getId());
            i.setProductId(p.getId());
            i.setSku(p.getSku());
            i.setProductName(p.getName());
            i.setUnitPrice(Money.of(p.getPrice()));
            i.setQuantity(line.getQuantity());
            i.setLineTotal(Money.of(p.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()))));
            items.save(i);
        }
        if (applied != null) {
            coupons.consume(applied.coupon());
        }
        lifecycle.log(o, null, OrderStatus.PENDING_PAYMENT, "Pedido realizado", userId, now);
        carts.clear(userId);
        return o;
    }

    public static String number(Long id) {
        return "VL" + String.format("%06d", 100000 + id);
    }

    private void fillAddress(Order o, Long userId, OrderDtos.CheckoutRequest req) {
        if (req.addressId() != null) {
            Address a = addresses.findByIdAndUserId(req.addressId(), userId)
                    .orElseThrow(() -> ApiException.badRequest("ADDRESS", "Endereço não encontrado."));
            o.setShipRecipient(a.getRecipient());
            o.setShipZipCode(a.getZipCode());
            o.setShipStreet(a.getStreet());
            o.setShipNumber(a.getNumber());
            o.setShipComplement(a.getComplement());
            o.setShipDistrict(a.getDistrict());
            o.setShipCity(a.getCity());
            o.setShipState(a.getState());
            return;
        }
        AccountDtos.AddressInput a = req.address();
        if (a == null) {
            throw ApiException.badRequest("ADDRESS", "Informe o endereço de entrega.");
        }
        o.setShipRecipient(Texts.clean(a.recipient()));
        o.setShipZipCode(zip(a.zipCode()));
        o.setShipStreet(Texts.clean(a.street()));
        o.setShipNumber(Texts.clean(a.number()));
        o.setShipComplement(Texts.clean(a.complement()));
        o.setShipDistrict(Texts.clean(a.district()));
        o.setShipCity(Texts.clean(a.city()));
        o.setShipState(a.state().trim().toUpperCase(Locale.ROOT));
    }

    public static String zip(String v) {
        String d = Texts.digits(v);
        return d.length() == 8 ? d.substring(0, 5) + "-" + d.substring(5) : v;
    }

    @Transactional(readOnly = true)
    public Order mine(String number, Long userId) {
        Order o = orders.findByNumber(number).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (!o.getUserId().equals(userId)) {
            throw ApiException.notFound("Pedido não encontrado.");
        }
        return o;
    }

    @Transactional(readOnly = true)
    public Order byNumber(String number) {
        return orders.findByNumber(number).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
    }

    @Transactional
    public Order cancelByCustomer(String number, Long userId) {
        Order o = mine(number, userId);
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT && o.getStatus() != OrderStatus.PAID) {
            throw ApiException.conflict("NOT_CANCELABLE", "Este pedido já está em separação e não pode ser cancelado aqui.");
        }
        return cancel(o, "Cancelado pelo cliente", userId);
    }

    @Transactional
    public Order changeStatus(String number, OrderDtos.StatusUpdate in, Long actorId) {
        Order o = byNumber(number);
        if (in.trackingCode() != null && !in.trackingCode().isBlank()) {
            o.setTrackingCode(in.trackingCode().trim().toUpperCase(Locale.ROOT));
        }
        String note = Texts.clean(in.note());
        if (in.status() == OrderStatus.CANCELED) {
            return cancel(o, note == null ? "Cancelado pela loja" : note, actorId);
        }
        if (in.status() == OrderStatus.REFUNDED) {
            if (!o.getStatus().next().contains(OrderStatus.REFUNDED)) {
                throw ApiException.conflict("INVALID_STATUS", "Só pedidos entregues podem ser estornados por aqui.");
            }
            payments.refund(o);
            return lifecycle.move(o, OrderStatus.REFUNDED, note == null ? "Valor estornado ao cliente" : note, actorId);
        }
        if (in.status() == OrderStatus.PAID) {
            throw ApiException.badRequest("PAYMENT", "O pagamento é confirmado pelo provedor, não manualmente.");
        }
        return lifecycle.move(o, in.status(), note, actorId);
    }

    private Order cancel(Order o, String note, Long actorId) {
        if (!o.getStatus().next().contains(OrderStatus.CANCELED)) {
            throw ApiException.conflict("NOT_CANCELABLE", "Este pedido não pode mais ser cancelado.");
        }
        if (o.getStatus().isPaid()) {
            payments.refund(o);
        } else {
            payments.cancelPending(o);
        }
        return lifecycle.move(o, OrderStatus.CANCELED, note, actorId);
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional
    public void expireUnpaid() {
        LocalDateTime limit = LocalDateTime.now(clock).minusMinutes(props.orders().paymentWindowMinutes());
        for (Order o : orders.findByStatusAndPlacedAtBefore(OrderStatus.PENDING_PAYMENT, limit)) {
            try {
                payments.cancelPending(o);
                lifecycle.move(o, OrderStatus.CANCELED, "Pagamento não recebido no prazo", null);
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", o.getNumber(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.Summary> search(Filter f) {
        int size = Math.max(1, Math.min(f.size(), 100));
        int page = Math.max(0, f.page());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> root = cq.from(Order.class);
        cq.select(root).where(predicates(cb, root, f)).orderBy(cb.desc(root.get("placedAt")), cb.desc(root.get("id")));
        List<Order> list = em.createQuery(cq).setFirstResult(page * size).setMaxResults(size).getResultList();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Order> cr = count.from(Order.class);
        count.select(cb.count(cr)).where(predicates(cb, cr, f));
        long total = em.createQuery(count).getSingleResult();
        Map<Long, User> owners = new HashMap<>();
        Set<Long> ids = list.stream().map(Order::getUserId).collect(Collectors.toSet());
        users.findAllById(ids).forEach(u -> owners.put(u.getId(), u));
        Map<Long, Integer> counts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : items.quantities(list.stream().map(Order::getId).toList())) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        List<OrderDtos.Summary> out = list.stream().map(o -> {
            User u = owners.get(o.getUserId());
            return new OrderDtos.Summary(o.getId(), o.getNumber(), o.getStatus(), OrderLifecycle.label(o.getStatus()), o.getTotal(),
                    counts.getOrDefault(o.getId(), 0), Times.instant(o.getPlacedAt()), o.getUserId(),
                    u == null ? null : u.getFullName(), u == null ? null : u.getEmail());
        }).toList();
        return PageResponse.of(out, page, size, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<Order> root, Filter f) {
        List<Predicate> p = new ArrayList<>();
        if (f.userId() != null) {
            p.add(cb.equal(root.get("userId"), f.userId()));
        }
        if (f.status() != null) {
            p.add(cb.equal(root.get("status"), f.status()));
        }
        if (f.from() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("placedAt"), f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            p.add(cb.lessThan(root.get("placedAt"), f.to().plusDays(1).atStartOfDay()));
        }
        String like = Texts.like(f.q());
        if (like != null) {
            List<Long> matching = users.idsMatching(like, PageRequest.of(0, 500));
            Predicate byNumber = cb.like(cb.lower(root.get("number")), like, '\\');
            p.add(matching.isEmpty() ? byNumber : cb.or(byNumber, root.get("userId").in(matching)));
        }
        return p.toArray(Predicate[]::new);
    }

    @Transactional(readOnly = true)
    public OrderDtos.Detail detail(Order o, boolean backoffice) {
        List<OrderDtos.Item> lines = new ArrayList<>();
        for (OrderItem i : items.findByOrderIdOrderByIdAsc(o.getId())) {
            Product p = i.getProductId() == null ? null : products.findById(i.getProductId()).orElse(null);
            lines.add(new OrderDtos.Item(i.getProductId(), i.getSku(), i.getProductName(), i.getUnitPrice(), i.getQuantity(),
                    i.getLineTotal(), p == null ? null : p.getImageUrl(), p == null ? null : p.getSlug()));
        }
        Map<Long, String> names = new HashMap<>();
        List<OrderDtos.History> timeline = new ArrayList<>();
        for (OrderStatusHistory h : history.findByOrderIdOrderByIdAsc(o.getId())) {
            String by = h.getUserId() == null ? "Velo" : names.computeIfAbsent(h.getUserId(),
                    id -> users.findById(id).map(User::getFullName).orElse("—"));
            timeline.add(new OrderDtos.History(h.getFromStatus(), h.getToStatus(), OrderLifecycle.label(h.getToStatus()), h.getNote(),
                    by, Times.instant(h.getCreatedAt())));
        }
        User u = users.findById(o.getUserId()).orElse(null);
        OrderDtos.Customer customer = u == null ? null : new OrderDtos.Customer(u.getId(), u.getFullName(), u.getEmail(),
                backoffice ? u.getPhone() : null);
        boolean canPay = o.getStatus() == OrderStatus.PENDING_PAYMENT;
        boolean canCancel = o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAID
                || (backoffice && o.getStatus() == OrderStatus.PROCESSING);
        List<OrderStatus> next = backoffice ? o.getStatus().next().stream().filter(s -> s != OrderStatus.PAID).sorted().toList()
                : List.of();
        LocalDateTime deadline = canPay ? o.getPlacedAt().plusMinutes(props.orders().paymentWindowMinutes()) : null;
        return new OrderDtos.Detail(o.getId(), o.getNumber(), o.getStatus(), OrderLifecycle.label(o.getStatus()), o.getSubtotal(),
                o.getDiscount(), o.getShippingCost(), o.getTotal(), o.getCouponCode(), o.getShipRecipient(), o.getShipZipCode(),
                o.getShipStreet(), o.getShipNumber(), o.getShipComplement(), o.getShipDistrict(), o.getShipCity(), o.getShipState(),
                o.getTrackingCode(), o.getNotes(), Times.instant(o.getPlacedAt()), Times.instant(o.getPaidAt()),
                Times.instant(o.getShippedAt()), Times.instant(o.getDeliveredAt()), Times.instant(o.getCanceledAt()),
                Times.instant(deadline), lines, timeline, payments.views(o.getId()), customer, canPay, canCancel, next);
    }
}
