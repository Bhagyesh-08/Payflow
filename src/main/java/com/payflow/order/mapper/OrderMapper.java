package com.payflow.order.mapper;

import com.payflow.order.dto.OrderResponse;
import com.payflow.order.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .merchantId(order.getMerchant().getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .description(order.getDescription())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
