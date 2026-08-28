package com.arda.iyzico.project.controllers;

import com.arda.iyzico.project.dto.PurchaseOrderResponse;
import com.arda.iyzico.project.services.PurchaseFlowService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final PurchaseFlowService purchaseFlowService;

    @GetMapping("/orders")
    public List<PurchaseOrderResponse> getOrders() {
        return purchaseFlowService.getOrders();
    }
}
