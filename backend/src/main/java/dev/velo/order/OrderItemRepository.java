package dev.velo.order;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByProductId(Long productId);

    @Query("select i.orderId, sum(i.quantity) from OrderItem i where i.orderId in :ids group by i.orderId")
    List<Object[]> quantities(@Param("ids") Collection<Long> orderIds);
}
