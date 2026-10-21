package dev.velo.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findTop50ByProductIdOrderByIdDesc(Long productId);

    boolean existsByProductIdAndTypeNot(Long productId, MovementType type);

    void deleteByProductId(Long productId);
}
