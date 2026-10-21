package dev.velo.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select i from CartItem i join fetch i.product p left join fetch p.category where i.cartId = :cartId order by i.id")
    List<CartItem> findWithProducts(@Param("cartId") Long cartId);

    @Query("select i from CartItem i where i.cartId = :cartId and i.product.id = :productId")
    Optional<CartItem> findLine(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem i where i.cartId = :cartId")
    int clear(@Param("cartId") Long cartId);

    @Modifying
    @Query("delete from CartItem i where i.product.id = :productId")
    int deleteByProduct(@Param("productId") Long productId);
}
