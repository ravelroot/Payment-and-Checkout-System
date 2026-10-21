package dev.velo.seed;

import dev.velo.catalog.Category;
import dev.velo.catalog.CategoryRepository;
import dev.velo.catalog.InventoryMovement;
import dev.velo.catalog.InventoryMovementRepository;
import dev.velo.catalog.MovementType;
import dev.velo.catalog.Product;
import dev.velo.catalog.ProductRepository;
import dev.velo.common.Money;
import dev.velo.common.Texts;
import dev.velo.config.VeloProperties;
import dev.velo.coupon.Coupon;
import dev.velo.coupon.CouponRepository;
import dev.velo.coupon.DiscountType;
import dev.velo.order.Order;
import dev.velo.order.OrderItem;
import dev.velo.order.OrderItemRepository;
import dev.velo.order.OrderLifecycle;
import dev.velo.order.OrderRepository;
import dev.velo.order.OrderService;
import dev.velo.order.OrderStatus;
import dev.velo.order.OrderStatusHistory;
import dev.velo.order.OrderStatusHistoryRepository;
import dev.velo.order.Pricing;
import dev.velo.payment.Payment;
import dev.velo.payment.PaymentMethod;
import dev.velo.payment.PaymentProvider;
import dev.velo.payment.PaymentRepository;
import dev.velo.payment.PaymentStatus;
import dev.velo.user.Address;
import dev.velo.user.AddressRepository;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import dev.velo.user.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Velo@2026";
    public static final String ADMIN_EMAIL = "admin@velo.dev";
    public static final String STAFF_EMAIL = "equipe@velo.dev";
    public static final String CUSTOMER_EMAIL = "cliente@velo.dev";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final VeloProperties props;
    private final UserRepository users;
    private final AddressRepository addresses;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final InventoryMovementRepository movements;
    private final CouponRepository coupons;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderStatusHistoryRepository history;
    private final PaymentRepository payments;
    private final PasswordEncoder encoder;
    private final Pricing pricing;
    private final Clock clock;

    public DataSeeder(VeloProperties props, UserRepository users, AddressRepository addresses, CategoryRepository categories,
                      ProductRepository products, InventoryMovementRepository movements, CouponRepository coupons,
                      OrderRepository orders, OrderItemRepository orderItems, OrderStatusHistoryRepository history,
                      PaymentRepository payments, PasswordEncoder encoder, Pricing pricing, Clock clock) {
        this.props = props;
        this.users = users;
        this.addresses = addresses;
        this.categories = categories;
        this.products = products;
        this.movements = movements;
        this.coupons = coupons;
        this.orders = orders;
        this.orderItems = orderItems;
        this.history = history;
        this.payments = payments;
        this.encoder = encoder;
        this.pricing = pricing;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed() || users.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String hash = encoder.encode(DEMO_PASSWORD);
        User admin = user("Ana Admin", ADMIN_EMAIL, UserRole.ADMIN, hash, now.minusDays(60));
        user("Equipe Velo", STAFF_EMAIL, UserRole.STAFF, hash, now.minusDays(45));
        User customer = user("Clara Cliente", CUSTOMER_EMAIL, UserRole.CUSTOMER, hash, now.minusDays(40));
        address(customer, "Casa", "Clara Cliente", "22240-003", "Rua das Laranjeiras", "120", "Apto 302", "Laranjeiras",
                "Rio de Janeiro", "RJ", true);
        address(customer, "Trabalho", "Clara Cliente", "20040-002", "Avenida Rio Branco", "45", "Sala 1201", "Centro",
                "Rio de Janeiro", "RJ", false);

        String[][] people = {
            {"Bruno Lima", "bruno.lima"}, {"Carla Mendes", "carla.mendes"}, {"Diego Rocha", "diego.rocha"},
            {"Eduarda Alves", "eduarda.alves"}, {"Felipe Costa", "felipe.costa"}, {"Gabriela Nunes", "gabriela.nunes"},
            {"Heitor Prado", "heitor.prado"}, {"Isabela Duarte", "isabela.duarte"}
        };
        Random random = new Random(7);
        List<User> buyers = new ArrayList<>(List.of(customer));
        String[][] cities = {{"São Paulo", "SP", "01310-100", "Avenida Paulista", "Bela Vista"},
            {"Belo Horizonte", "MG", "30130-010", "Avenida Afonso Pena", "Centro"},
            {"Curitiba", "PR", "80020-000", "Rua XV de Novembro", "Centro"},
            {"Niterói", "RJ", "24020-007", "Rua da Conceição", "Centro"},
            {"Porto Alegre", "RS", "90010-150", "Rua dos Andradas", "Centro Histórico"}};
        for (String[] p : people) {
            User u = user(p[0], p[1] + "@exemplo.com.br", UserRole.CUSTOMER, hash, now.minusDays(5 + random.nextInt(50)));
            String[] c = cities[random.nextInt(cities.length)];
            address(u, "Casa", p[0], c[2], c[3], String.valueOf(10 + random.nextInt(900)), null, c[4], c[0], c[1], true);
            buyers.add(u);
        }

        Category casa = category("Casa", "Objetos simples para o dia a dia", 1);
        Category escritorio = category("Escritório", "Mesa organizada, cabeça tranquila", 2);
        Category audio = category("Áudio", "Som limpo, sem excesso", 3);
        Category acessorios = category("Acessórios", "Para levar com você", 4);
        Category cafe = category("Café", "Do grão à xícara", 5);
        Category bemEstar = category("Bem-estar", "Pausas que fazem diferença", 6);

        List<Product> catalog = new ArrayList<>();
        catalog.add(product(casa, "Caneca de Cerâmica Fosca", "Caneca de 320 ml em cerâmica com acabamento fosco e alça confortável. Vai ao micro-ondas e à lava-louças.", "89.90", null, 48, 420, true, "caneca"));
        catalog.add(product(casa, "Luminária de Mesa Arco", "Luminária de alumínio com braço articulado e luz quente regulável em três intensidades.", "349.00", "399.00", 12, 1800, true, "luminaria"));
        catalog.add(product(casa, "Vaso de Concreto Baixo", "Vaso artesanal de concreto para suculentas e cactos, com furo de drenagem.", "79.00", null, 30, 900, false, "vaso"));
        catalog.add(product(casa, "Vela Aromática Linho", "Vela de cera vegetal com notas de linho e algodão. Até 40 horas de queima.", "64.90", null, 4, 300, false, "vela"));
        catalog.add(product(casa, "Almofada Texturizada", "Capa em algodão com textura canelada e enchimento de fibra siliconada. 45 × 45 cm.", "119.00", null, 22, 600, false, "almofada"));
        catalog.add(product(escritorio, "Caderno Pontilhado A5", "Capa dura, 160 páginas de papel 120 g/m² pontilhado, fita marcadora e elástico.", "59.90", null, 80, 350, true, "caderno"));
        catalog.add(product(escritorio, "Caneta Metal Grafite", "Caneta esferográfica em alumínio com tinta preta de secagem rápida.", "39.90", null, 120, 30, false, "caneta"));
        catalog.add(product(escritorio, "Teclado Compacto Sem Fio", "Teclado 75% com conexão Bluetooth para até três aparelhos e bateria recarregável.", "429.00", "499.00", 9, 650, true, "teclado"));
        catalog.add(product(escritorio, "Mouse Silencioso", "Mouse sem fio com clique silencioso e sensor de 4000 DPI.", "149.00", null, 3, 90, false, "mouse"));
        catalog.add(product(escritorio, "Suporte para Notebook", "Suporte dobrável de alumínio com seis níveis de altura.", "189.00", null, 18, 700, false, "suporte"));
        catalog.add(product(audio, "Fone Over-ear Studio", "Fone fechado com cancelamento de ruído, 40 horas de bateria e estojo rígido.", "899.00", "1099.00", 7, 280, true, "fone"));
        catalog.add(product(audio, "Caixa de Som Portátil", "Caixa resistente à água com som 360° e 12 horas de bateria.", "459.00", null, 14, 540, true, "caixa-som"));
        catalog.add(product(audio, "Fone Intra-auricular", "Fones sem fio com estojo de carga, controle por toque e microfone duplo.", "349.00", null, 25, 60, false, "fone-intra"));
        catalog.add(product(audio, "Toca-discos Compacto", "Toca-discos de mesa com pré-amplificador embutido e saída Bluetooth.", "1290.00", null, 2, 3600, false, "toca-discos"));
        catalog.add(product(acessorios, "Mochila Urbana 20 L", "Mochila impermeável com compartimento acolchoado para notebook de até 15\".", "329.00", null, 16, 900, true, "mochila"));
        catalog.add(product(acessorios, "Carteira Slim de Couro", "Carteira minimalista para até oito cartões e notas dobradas.", "149.00", null, 34, 80, false, "carteira"));
        catalog.add(product(acessorios, "Relógio Analógico Minimal", "Caixa de aço de 38 mm, pulseira de couro e vidro de safira.", "690.00", "790.00", 6, 120, true, "relogio"));
        catalog.add(product(acessorios, "Óculos de Sol Redondo", "Armação de acetato e lentes com proteção UV400.", "259.00", null, 11, 60, false, "oculos"));
        catalog.add(product(cafe, "Prensa Francesa 600 ml", "Prensa de vidro borossilicato com filtro de aço inox.", "159.00", null, 20, 700, true, "prensa"));
        catalog.add(product(cafe, "Moedor Manual de Café", "Moedor com mó cônica de cerâmica e regulagem de moagem.", "239.00", null, 8, 500, false, "moedor"));
        catalog.add(product(cafe, "Chaleira Bico de Ganso", "Chaleira de 1 L com bico fino para métodos filtrados.", "279.00", "319.00", 13, 850, false, "chaleira"));
        catalog.add(product(cafe, "Café Especial em Grãos 250 g", "Grãos de torra média do Sul de Minas, notas de chocolate e caramelo.", "54.90", null, 60, 260, true, "cafe-graos"));
        catalog.add(product(bemEstar, "Tapete de Yoga Natural", "Tapete de borracha natural antiderrapante, 4 mm de espessura.", "289.00", null, 10, 2200, false, "tapete-yoga"));
        catalog.add(product(bemEstar, "Garrafa Térmica 750 ml", "Garrafa de aço inox que mantém a temperatura por até 24 horas.", "139.00", null, 40, 380, true, "garrafa"));

        coupon("BEMVINDO10", "10% na primeira compra", DiscountType.PERCENT, "10", null, null, true, null);
        coupon("VELO20", "R$ 20 de desconto acima de R$ 150", DiscountType.FIXED, "20", "150", 200, true, null);
        coupon("INVERNO15", "Campanha encerrada", DiscountType.PERCENT, "15", null, null, false, now.minusDays(10));

        int created = 0;
        for (int i = 0; i < 64; i++) {
            User buyer = buyers.get(random.nextInt(buyers.size()));
            int daysAgo = (int) Math.floor(Math.pow(random.nextDouble(), 1.3) * 29);
            LocalDateTime placed = now.minusDays(daysAgo).withHour(8 + random.nextInt(14)).withMinute(random.nextInt(60));
            if (placed.isAfter(now)) {
                placed = now.minusMinutes(20 + random.nextInt(120));
            }
            OrderStatus status = pickStatus(daysAgo, random);
            sampleOrder(buyer, catalog, placed, status, random, now);
            created++;
        }
        log.info("Velo demo data ready: {} users, {} products, {} orders. Admin: {} / {}", users.count(), catalog.size(),
                created, admin.getEmail(), DEMO_PASSWORD);
    }

    private OrderStatus pickStatus(int daysAgo, Random r) {
        double x = r.nextDouble();
        if (daysAgo == 0) {
            return x < 0.3 ? OrderStatus.PENDING_PAYMENT : x < 0.75 ? OrderStatus.PAID : OrderStatus.PROCESSING;
        }
        if (daysAgo < 4) {
            return x < 0.1 ? OrderStatus.CANCELED : x < 0.35 ? OrderStatus.PROCESSING : x < 0.75 ? OrderStatus.SHIPPED
                    : OrderStatus.DELIVERED;
        }
        return x < 0.08 ? OrderStatus.CANCELED : x < 0.12 ? OrderStatus.REFUNDED : x < 0.2 ? OrderStatus.SHIPPED
                : OrderStatus.DELIVERED;
    }

    private void sampleOrder(User buyer, List<Product> catalog, LocalDateTime placed, OrderStatus status, Random r,
                             LocalDateTime now) {
        Address a = addresses.findByUserIdOrderByDefaultAddressDescIdAsc(buyer.getId()).get(0);
        int lines = 1 + r.nextInt(3);
        List<Product> chosen = new ArrayList<>();
        while (chosen.size() < lines) {
            Product p = catalog.get(r.nextInt(catalog.size()));
            if (!chosen.contains(p)) {
                chosen.add(p);
            }
        }
        BigDecimal subtotal = Money.ZERO;
        int[] qty = new int[chosen.size()];
        int count = 0;
        for (int i = 0; i < chosen.size(); i++) {
            qty[i] = chosen.get(i).getPrice().compareTo(new BigDecimal("200")) > 0 ? 1 : 1 + r.nextInt(2);
            subtotal = subtotal.add(chosen.get(i).getPrice().multiply(BigDecimal.valueOf(qty[i])));
            count += qty[i];
        }
        BigDecimal shipping = pricing.shipping(subtotal, count);
        Order o = new Order();
        o.setUserId(buyer.getId());
        o.setStatus(status);
        o.setSubtotal(Money.of(subtotal));
        o.setDiscount(Money.ZERO);
        o.setShippingCost(shipping);
        o.setTotal(Money.of(subtotal.add(shipping)));
        o.setShipRecipient(a.getRecipient());
        o.setShipZipCode(a.getZipCode());
        o.setShipStreet(a.getStreet());
        o.setShipNumber(a.getNumber());
        o.setShipComplement(a.getComplement());
        o.setShipDistrict(a.getDistrict());
        o.setShipCity(a.getCity());
        o.setShipState(a.getState());
        o.setPlacedAt(placed);
        o.setNumber("T" + Long.toHexString(r.nextLong()).substring(0, 10));
        o = orders.save(o);
        o.setNumber(OrderService.number(o.getId()));
        for (int i = 0; i < chosen.size(); i++) {
            Product p = chosen.get(i);
            OrderItem item = new OrderItem();
            item.setOrderId(o.getId());
            item.setProductId(p.getId());
            item.setSku(p.getSku());
            item.setProductName(p.getName());
            item.setUnitPrice(p.getPrice());
            item.setQuantity(qty[i]);
            item.setLineTotal(Money.of(p.getPrice().multiply(BigDecimal.valueOf(qty[i]))));
            orderItems.save(item);
        }
        LocalDateTime t = placed;
        hist(o, null, OrderStatus.PENDING_PAYMENT, "Pedido realizado", t);
        if (status == OrderStatus.PENDING_PAYMENT) {
            orders.save(o);
            return;
        }
        boolean paid = status != OrderStatus.CANCELED || r.nextBoolean();
        PaymentMethod method = r.nextDouble() < 0.6 ? PaymentMethod.CARD : r.nextBoolean() ? PaymentMethod.PIX : PaymentMethod.BOLETO;
        Payment pay = new Payment();
        pay.setOrderId(o.getId());
        pay.setProvider(PaymentProvider.SIMULATED);
        pay.setMethod(method);
        pay.setAmount(o.getTotal());
        pay.setInstallments(method == PaymentMethod.CARD && o.getTotal().compareTo(new BigDecimal("300")) > 0 ? 3 : 1);
        pay.setExternalId("seed_" + o.getId());
        pay.setCreatedAt(t.plusMinutes(3));
        if (method == PaymentMethod.CARD) {
            pay.setCardBrand(r.nextBoolean() ? "Visa" : "Mastercard");
            pay.setCardLast4(String.format("%04d", r.nextInt(10000)));
        }
        if (paid) {
            t = t.plusMinutes(3 + r.nextInt(40));
            pay.setStatus(PaymentStatus.PAID);
            pay.setPaidAt(t);
            o.setPaidAt(t);
            hist(o, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, method == PaymentMethod.CARD ? "Pago no cartão" : method == PaymentMethod.PIX ? "Pago via PIX" : "Boleto compensado", t);
        } else {
            pay.setStatus(PaymentStatus.CANCELED);
        }
        OrderStatus last = paid ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT;
        List<OrderStatus> path = switch (status) {
            case PROCESSING -> List.of(OrderStatus.PROCESSING);
            case SHIPPED -> List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case REFUNDED -> List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
            case CANCELED -> List.of(OrderStatus.CANCELED);
            default -> List.of();
        };
        for (OrderStatus s : path) {
            t = t.plusHours(s == OrderStatus.DELIVERED ? 30 + r.nextInt(60) : 2 + r.nextInt(20));
            if (t.isAfter(now)) {
                t = now.minusMinutes(5);
            }
            switch (s) {
                case SHIPPED -> {
                    o.setTrackingCode("BR" + (100000000 + r.nextInt(899999999)) + "VL");
                    o.setShippedAt(t);
                }
                case DELIVERED -> o.setDeliveredAt(t);
                case CANCELED, REFUNDED -> o.setCanceledAt(t);
                default -> {
                }
            }
            hist(o, last, s, s == OrderStatus.CANCELED ? "Cancelado pelo cliente" : s == OrderStatus.REFUNDED ? "Devolução aprovada" : null, t);
            last = s;
        }
        if (status == OrderStatus.REFUNDED || (status == OrderStatus.CANCELED && paid)) {
            pay.setStatus(PaymentStatus.REFUNDED);
            pay.setRefundedAmount(pay.getAmount());
        }
        payments.save(pay);
        orders.save(o);
    }

    private void hist(Order o, OrderStatus from, OrderStatus to, String note, LocalDateTime at) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(o.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(note == null ? OrderLifecycle.label(to) : note);
        h.setCreatedAt(at);
        history.save(h);
    }

    private User user(String name, String email, UserRole role, String hash, LocalDateTime created) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(hash);
        u.setActive(true);
        u.setCreatedAt(created);
        return users.save(u);
    }

    private void address(User u, String label, String recipient, String zip, String street, String number, String complement,
                         String district, String city, String state, boolean def) {
        Address a = new Address();
        a.setUserId(u.getId());
        a.setLabel(label);
        a.setRecipient(recipient);
        a.setZipCode(zip);
        a.setStreet(street);
        a.setNumber(number);
        a.setComplement(complement);
        a.setDistrict(district);
        a.setCity(city);
        a.setState(state);
        a.setDefaultAddress(def);
        addresses.save(a);
    }

    private Category category(String name, String description, int order) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(Texts.slug(name));
        c.setDescription(description);
        c.setSortOrder(order);
        c.setActive(true);
        return categories.save(c);
    }

    private Product product(Category c, String name, String description, String price, String compareAt, int stock, int grams,
                            boolean featured, String image) {
        Product p = new Product();
        p.setCategory(c);
        p.setName(name);
        p.setSlug(Texts.slug(name));
        p.setSku(String.format("VL-%s-%03d", Texts.slug(c.getName()).replace("-", "").substring(0, 3).toUpperCase(),
                products.count() + 1));
        p.setDescription(description);
        p.setPrice(Money.of(price));
        p.setCompareAtPrice(compareAt == null ? null : Money.of(compareAt));
        p.setStock(stock);
        p.setLowStockThreshold(5);
        p.setWeightGrams(grams);
        p.setActive(true);
        p.setFeatured(featured);
        p.setImageUrl("/products/" + image + ".svg");
        Product saved = products.save(p);
        InventoryMovement m = new InventoryMovement();
        m.setProductId(saved.getId());
        m.setType(MovementType.IN);
        m.setQuantity(stock);
        m.setStockAfter(stock);
        m.setReason("Estoque inicial");
        m.setCreatedAt(LocalDateTime.now(clock).minusDays(60));
        movements.save(m);
        return saved;
    }

    private void coupon(String code, String description, DiscountType type, String value, String min, Integer maxUses,
                        boolean active, LocalDateTime endsAt) {
        Coupon c = new Coupon();
        c.setCode(code);
        c.setDescription(description);
        c.setType(type);
        c.setValue(Money.of(value));
        c.setMinSubtotal(min == null ? null : Money.of(min));
        c.setMaxUses(maxUses);
        c.setActive(active);
        c.setEndsAt(endsAt);
        coupons.save(c);
    }
}
