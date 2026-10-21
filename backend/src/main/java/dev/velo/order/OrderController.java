package dev.velo.order;

import dev.velo.auth.CurrentUser;
import dev.velo.common.PageResponse;
import dev.velo.payment.PaymentRequest;
import dev.velo.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final PaymentService payments;

    public OrderController(OrderService orders, PaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.Detail checkout(@Valid @RequestBody OrderDtos.CheckoutRequest in) {
        Order o = orders.checkout(CurrentUser.id(), in);
        return orders.detail(o, false);
    }

    @GetMapping
    public PageResponse<OrderDtos.Summary> mine(@RequestParam(required = false) OrderStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return orders.search(new OrderService.Filter(CurrentUser.id(), status, null, null, null, page, size));
    }

    @GetMapping("/{number}")
    public OrderDtos.Detail get(@PathVariable String number) {
        return orders.detail(orders.mine(number, CurrentUser.id()), false);
    }

    @PostMapping("/{number}/payments")
    public OrderDtos.Detail pay(@PathVariable String number, @Valid @RequestBody PaymentRequest in) {
        Order o = orders.mine(number, CurrentUser.id());
        payments.pay(o, in);
        return orders.detail(orders.mine(number, CurrentUser.id()), false);
    }

    @PostMapping("/{number}/cancel")
    public OrderDtos.Detail cancel(@PathVariable String number) {
        return orders.detail(orders.cancelByCustomer(number, CurrentUser.id()), false);
    }
}
