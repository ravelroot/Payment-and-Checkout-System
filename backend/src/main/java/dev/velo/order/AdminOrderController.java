package dev.velo.order;

import dev.velo.auth.CurrentUser;
import dev.velo.common.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public PageResponse<OrderDtos.Summary> list(@RequestParam(required = false) OrderStatus status,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) Long customerId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return orders.search(new OrderService.Filter(customerId, status, q, from, to, page, size));
    }

    @GetMapping("/{number}")
    public OrderDtos.Detail get(@PathVariable String number) {
        return orders.detail(orders.byNumber(number), true);
    }

    @PutMapping("/{number}/status")
    public OrderDtos.Detail status(@PathVariable String number, @Valid @RequestBody OrderDtos.StatusUpdate in) {
        orders.changeStatus(number, in, CurrentUser.id());
        return orders.detail(orders.byNumber(number), true);
    }
}
