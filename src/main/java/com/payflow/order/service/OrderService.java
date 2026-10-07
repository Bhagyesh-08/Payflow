package com.payflow.order.service;

import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.service.MerchantService;
import com.payflow.order.dto.CreateOrderRequest;
import com.payflow.order.dto.OrderResponse;
import com.payflow.order.entity.Order;
import com.payflow.order.enums.OrderStatus;
import com.payflow.order.mapper.OrderMapper;
import com.payflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final MerchantService merchantService;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for merchant ID: {}", request.getMerchantId());

        Merchant merchant = merchantService.getMerchantEntityById(request.getMerchantId());

        Order order = Order.builder()
                .orderNumber(IdGenerator.generateOrderNumber())
                .merchant(merchant)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(OrderStatus.CREATED)
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .description(request.getDescription())
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created successfully: {}", saved.getOrderNumber());

        return orderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Order getOrderEntityById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        
        // Ensure merchant is loaded for downstream processing
        if (order.getMerchant() != null) {
            order.getMerchant().getId(); // Force lazy loading
        }
        
        return order;
    }
}
