package dev.velo.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findFirstByProviderAndExternalId(PaymentProvider provider, String externalId);
}
