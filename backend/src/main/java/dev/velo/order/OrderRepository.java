package dev.velo.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByNumber(String number);

    List<Order> findByStatusAndPlacedAtBefore(OrderStatus status, LocalDateTime before);

    @Query("select o from Order o where o.placedAt >= :from")
    List<Order> placedSince(@Param("from") LocalDateTime from);

    long countByUserId(Long userId);

    long countByStatus(OrderStatus status);

    List<Order> findTop8ByOrderByPlacedAtDesc();
}
