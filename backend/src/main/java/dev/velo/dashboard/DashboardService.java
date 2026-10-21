package dev.velo.dashboard;

import dev.velo.catalog.Product;
import dev.velo.catalog.ProductRepository;
import dev.velo.common.Money;
import dev.velo.common.PageResponse;
import dev.velo.order.OrderDtos;
import dev.velo.order.OrderLifecycle;
import dev.velo.order.OrderService;
import dev.velo.order.OrderStatus;
import dev.velo.user.UserRole;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<OrderStatus> PAID = List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    public record Kpis(BigDecimal revenue, BigDecimal revenueChange, long orders, BigDecimal averageTicket, long newCustomers,
                       long toShip, long awaitingPayment, long lowStock) {
    }

    public record Day(LocalDate date, BigDecimal revenue, long orders) {
    }

    public record StatusCount(OrderStatus status, String label, long count) {
    }

    public record TopProduct(String name, long quantity, BigDecimal revenue) {
    }

    public record LowStock(Long id, String name, String sku, int stock, int threshold) {
    }

    public record View(int days, Kpis kpis, List<Day> sales, List<StatusCount> statuses, List<TopProduct> topProducts,
                       List<LowStock> lowStock, List<OrderDtos.Summary> recentOrders) {
    }

    private final EntityManager em;
    private final ProductRepository products;
    private final OrderService orders;
    private final Clock clock;

    public DashboardService(EntityManager em, ProductRepository products, OrderService orders, Clock clock) {
        this.em = em;
        this.products = products;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View view(int days) {
        int d = Math.max(7, Math.min(days, 90));
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate firstDay = today.minusDays(d - 1L);
        LocalDateTime from = utc(firstDay);
        LocalDateTime previousFrom = utc(firstDay.minusDays(d));

        Object[] current = totals(from, null);
        Object[] previous = totals(previousFrom, from);
        long count = ((Number) current[0]).longValue();
        BigDecimal revenue = Money.of((BigDecimal) current[1]);
        BigDecimal before = Money.of((BigDecimal) previous[1]);
        BigDecimal change = before.signum() == 0 ? null
                : revenue.subtract(before).multiply(new BigDecimal("100")).divide(before, 1, RoundingMode.HALF_UP);
        BigDecimal avg = count == 0 ? Money.ZERO : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        long newCustomers = em.createQuery("select count(u) from User u where u.role = :r and u.createdAt >= :from", Long.class)
                .setParameter("r", UserRole.CUSTOMER).setParameter("from", from).getSingleResult();
        long toShip = em.createQuery("select count(o) from Order o where o.status in :s", Long.class)
                .setParameter("s", List.of(OrderStatus.PAID, OrderStatus.PROCESSING)).getSingleResult();
        long awaiting = em.createQuery("select count(o) from Order o where o.status = :s", Long.class)
                .setParameter("s", OrderStatus.PENDING_PAYMENT).getSingleResult();

        Map<LocalDate, BigDecimal[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < d; i++) {
            byDay.put(firstDay.plusDays(i), new BigDecimal[] {Money.ZERO, BigDecimal.ZERO});
        }
        List<Object[]> rows = em.createQuery("select o.placedAt, o.total from Order o where o.placedAt >= :from and o.status in :s",
                Object[].class).setParameter("from", from).setParameter("s", PAID).getResultList();
        for (Object[] r : rows) {
            LocalDate day = ((LocalDateTime) r[0]).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDate();
            BigDecimal[] acc = byDay.get(day);
            if (acc != null) {
                acc[0] = acc[0].add((BigDecimal) r[1]);
                acc[1] = acc[1].add(BigDecimal.ONE);
            }
        }
        List<Day> sales = new ArrayList<>();
        byDay.forEach((k, v) -> sales.add(new Day(k, Money.of(v[0]), v[1].longValue())));

        Map<OrderStatus, Long> st = new EnumMap<>(OrderStatus.class);
        for (Object[] r : em.createQuery("select o.status, count(o) from Order o where o.placedAt >= :from group by o.status",
                Object[].class).setParameter("from", from).getResultList()) {
            st.put((OrderStatus) r[0], ((Number) r[1]).longValue());
        }
        List<StatusCount> statuses = new ArrayList<>();
        for (OrderStatus s : OrderStatus.values()) {
            statuses.add(new StatusCount(s, OrderLifecycle.label(s), st.getOrDefault(s, 0L)));
        }

        List<TopProduct> top = em.createQuery("select i.productName, sum(i.quantity), sum(i.lineTotal) from OrderItem i, Order o "
                        + "where i.orderId = o.id and o.placedAt >= :from and o.status in :s "
                        + "group by i.productName order by sum(i.lineTotal) desc", Object[].class)
                .setParameter("from", from).setParameter("s", PAID).setMaxResults(5).getResultList().stream()
                .map(r -> new TopProduct((String) r[0], ((Number) r[1]).longValue(), Money.of((BigDecimal) r[2])))
                .toList();

        List<Product> lowAll = products.findLowStock();
        List<LowStock> low = lowAll.stream().limit(6)
                .map((Product p) -> new LowStock(p.getId(), p.getName(), p.getSku(), p.getStock(), p.getLowStockThreshold()))
                .toList();

        PageResponse<OrderDtos.Summary> recent = orders.search(new OrderService.Filter(null, null, null, null, null, 0, 6));
        return new View(d, new Kpis(revenue, change, count, avg, newCustomers, toShip, awaiting, lowAll.size()), sales, statuses, top, low,
                recent.items());
    }

    private Object[] totals(LocalDateTime from, LocalDateTime to) {
        String jpql = "select count(o), coalesce(sum(o.total), 0) from Order o where o.placedAt >= :from and o.status in :s"
                + (to == null ? "" : " and o.placedAt < :to");
        var q = em.createQuery(jpql, Object[].class).setParameter("from", from).setParameter("s", PAID);
        if (to != null) {
            q.setParameter("to", to);
        }
        Object[] r = q.getSingleResult();
        if (!(r[1] instanceof BigDecimal)) {
            r[1] = new BigDecimal(r[1].toString());
        }
        return r;
    }

    private static LocalDateTime utc(LocalDate day) {
        return day.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
