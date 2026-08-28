package com.arda.iyzico.project.repositories;

import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findByBuyerEmailOrderByCreatedAtDesc(String buyerEmail);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findWithItemsById(Long id);

    Optional<PurchaseOrder> findByPaymentToken(String paymentToken);

    Optional<PurchaseOrder> findByConversationId(String conversationId);

    List<PurchaseOrder> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);
}
