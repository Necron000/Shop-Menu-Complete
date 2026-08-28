package com.arda.iyzico.project.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "purchase_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String buyerEmail;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column
    private String checkoutToken;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(unique = true)
    private String conversationId;

    private String paymentToken;
    private String paymentId;

    private String buyerName;
    private String buyerSurname;
    private String buyerPhone;
    private String buyerIdentityNo;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String country;
    private String zipCode;

    @Column(columnDefinition = "text")
    private String errorMessage;

    public void addItem(OrderItem item) {
        item.assignTo(this);
        this.items.add(item);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void markAwaitingPayment(String conversationId, String paymentToken) {
        this.conversationId = conversationId;
        this.paymentToken   = paymentToken;
        this.status         = PaymentStatus.AWAITING_PAYMENT;
    }

    public void markPaid(String paymentId) {
        this.paymentId    = paymentId;
        this.status       = PaymentStatus.PAID;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status       = PaymentStatus.FAILED;
    }

    public void markCancelled(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status       = PaymentStatus.CANCELLED;
    }

    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }
}
