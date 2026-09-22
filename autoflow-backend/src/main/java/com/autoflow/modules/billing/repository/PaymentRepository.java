package com.autoflow.modules.billing.repository;

import com.autoflow.modules.billing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByOrganizationId(UUID organizationId);

    List<Payment> findBySubscriptionId(UUID subscriptionId);

    boolean existsByProviderPaymentId(String providerPaymentId);
}
