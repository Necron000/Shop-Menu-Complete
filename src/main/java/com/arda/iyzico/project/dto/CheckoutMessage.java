package com.arda.iyzico.project.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutMessage {
    private Long orderId;
    private String buyerEmail;
    private List<Line> lines;
    private BigDecimal amount;
    private String currency;
    private String checkoutToken;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Line {
        private Long itemId;
        private Integer quantity;
    }
}
