package com.payflow.order.dto;

import com.payflow.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private OrderStatus status;
    private String customerEmail;
    private String customerPhone;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
