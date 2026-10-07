package com.payflow.payment.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.idempotency.IdempotencyService;
import com.payflow.event.service.PaymentEventService;
import com.payflow.infrastructure.gateway.PaymentGateway;
import com.payflow.merchant.entity.Merchant;
import com.payflow.order.entity.Order;
import com.payflow.order.service.OrderService;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.mapper.PaymentMapper;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.statemachine.PaymentStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentStateMachine stateMachine;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PaymentEventService paymentEventService;

    @InjectMocks
    private PaymentService paymentService;

    private CreatePaymentRequest request;
    private Order order;
    private Payment payment;
    private PaymentResponse response;

    @BeforeEach
    void setUp() {
        request = CreatePaymentRequest.builder()
                .orderId(1L)
                .paymentMethod("CARD")
                .build();

        Merchant merchant = Merchant.builder()
                .id(1L)
                .businessName("Test Merchant")
                .build();
                
        order = Order.builder()
                .id(1L)
                .orderNumber("ORD_123")
                .merchant(merchant)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .build();

        payment = Payment.builder()
                .id(1L)
                .paymentId("PAY_ABC123")
                .order(order)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .status(PaymentStatus.CREATED)
                .build();

        response = PaymentResponse.builder()
                .id(1L)
                .paymentId("PAY_ABC123")
                .orderId(1L)
                .amount(new BigDecimal("1000.00"))
                .status(PaymentStatus.CREATED)
                .build();
    }

    @Test
    void createPayment_Success() {
        when(orderService.getOrderEntityById(1L)).thenReturn(order);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);
        when(idempotencyService.checkIdempotency(anyLong(), anyString(), any())).thenReturn(null);

        PaymentResponse result = paymentService.createPayment(request, 1L, "test-key");

        assertThat(result).isNotNull();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPayment_PaymentExists_ThrowsException() {
        when(orderService.getOrderEntityById(1L)).thenReturn(order);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(idempotencyService.checkIdempotency(anyLong(), anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> paymentService.createPayment(request, 1L, "test-key"))
                .isInstanceOf(BusinessException.class);
    }
}
