package com.arda.iyzico.project.services;

import com.arda.iyzico.project.dto.PurchaseOrderResponse;
import java.util.List;

public interface PurchaseFlowService {
    List<PurchaseOrderResponse> getOrders();
}
