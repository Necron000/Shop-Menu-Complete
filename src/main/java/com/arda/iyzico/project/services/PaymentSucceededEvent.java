package com.arda.iyzico.project.services;

import com.arda.iyzico.project.models.OrderItem;
import com.arda.iyzico.project.models.PurchaseOrder;
import java.util.List;

/** Lines are snapshotted because the AFTER_COMMIT listener sees a detached order. */
public record PaymentSucceededEvent(PurchaseOrder order, List<OrderItem> items) {}
