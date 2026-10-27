package dev.velo.order;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByNumber(String number);

    /** Trava a linha do pedido até o fim da transação, para serializar pagamento, webhooks e expiração. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    List<Order> findByStatusAndPlacedAtBefore(OrderStatus status, LocalDateTime before);

    @Query("select o from Order o where o.placedAt >= :from")
    List<Order> placedSince(@Param("from") LocalDateTime from);

    long countByUserId(Long userId);

    long countByStatus(OrderStatus status);

    List<Order> findTop8ByOrderByPlacedAtDesc();
}
