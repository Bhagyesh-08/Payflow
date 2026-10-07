package com.payflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.idempotency.IdempotencyCachedResponse;
import com.payflow.common.idempotency.IdempotencyService;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.common.security.SecurityUtils;
import com.payflow.common.util.IdGenerator;
import com.payflow.event.domain.PaymentEventType;
import com.payflow.event.service.PaymentEventService;
import com.payflow.infrastructure.gateway.GatewayPaymentRequest;
import com.payflow.infrastructure.gateway.GatewayResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final PaymentStateMachine stateMachine;
    private final PaymentGateway paymentGateway;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final PaymentEventService paymentEventService;
    private final BusinessMetrics metrics;

    /**
     * Creates a payment with idempotency support.
     * 
     * Transaction Strategy:
     * - Uses REPEATABLE_READ isolation to prevent phantom reads
     * - Payment creation is atomic with idempotency record
     * - Gateway calls happen AFTER successful DB commit (simulated for now)
     * 
     * Idempotency:
     * - Checks Redis first for fast duplicate detection
     * - Falls back to database if Redis miss
     * - Stores result in both layers for future requests
     * 
     * Concurrency:
     * - Database unique constraint on (merchant_id, idempotency_key) prevents duplicates
     * - Optimistic locking (@Version) detects concurrent payment updates
     * 
     * @param request the payment creation request
     * @param merchantId the merchant ID (from authentication context)
     * @param idempotencyKey the idempotency key from header
     * @return payment response
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PaymentResponse createPayment(CreatePaymentRequest request, Long merchantId, String idempotencyKey) {
        MDC.put("merchantId", String.valueOf(merchantId));
        MDC.put("idempotencyKey", idempotencyKey);
        
        log.info("Creating payment: order_id={}", request.getOrderId());

        // Check idempotency (Redis + DB)
        IdempotencyCachedResponse cachedResponse = idempotencyService.checkIdempotency(
            merchantId, idempotencyKey, request);
        
        if (cachedResponse != null) {
            log.info("Returning cached payment response");
            try {
                return objectMapper.readValue(cachedResponse.getResponseBody(), PaymentResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize cached response", e);
                throw new BusinessException("IDEMPOTENCY_ERROR", "Failed to retrieve cached response");
            }
        }

        // Fetch order and validate
        Order order = orderService.getOrderEntityById(request.getOrderId());
        Merchant merchant = order.getMerchant();

        // Verify merchant owns this order
        if (!merchant.getId().equals(merchantId)) {
            throw new BusinessException("UNAUTHORIZED_ORDER", 
                "Order does not belong to the specified merchant");
        }

        // Check if payment already exists for this order
        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BusinessException("PAYMENT_EXISTS", 
                "Payment already exists for order: " + order.getOrderNumber());
        }

        // Create payment in CREATED state
        Payment payment = Payment.builder()
                .paymentId(IdGenerator.generatePaymentId())
                .order(order)
                .merchant(merchant)
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(PaymentStatus.CREATED)
                .paymentMethod(request.getPaymentMethod())
                .build();

        Payment saved = paymentRepository.save(payment);
        MDC.put("paymentId", saved.getPaymentId());
        
        log.info("Payment created: payment_id={}", saved.getPaymentId());
        metrics.incrementPaymentsCreated();

        // Create domain event (in same transaction)
        paymentEventService.createPaymentEvent(saved, PaymentEventType.PAYMENT_CREATED);

        // Prepare response
        PaymentResponse response = paymentMapper.toResponse(saved);
        
        // Store idempotency record (in separate transaction)
        try {
            String responseBody = objectMapper.writeValueAsString(response);
            idempotencyService.storeIdempotencyRecord(
                merchant, idempotencyKey, request, saved, 
                HttpStatus.CREATED.value(), responseBody);
        } catch (Exception e) {
            log.error("Failed to serialize response for idempotency storage", e);
            // Continue - idempotency storage failure shouldn't fail payment creation
        }

        return response;
    }

    /**
     * Initiates a payment with the gateway.
     * Transitions: CREATED -> PAYMENT_INITIATED
     * 
     * Transaction Strategy:
     * 1. Load payment (no transaction)
     * 2. Call gateway (NO transaction held)
     * 3. Persist result + event (atomic transaction)
     * 
     * @param paymentId the payment ID
     * @return updated payment response
     */
    public PaymentResponse initiatePayment(Long paymentId) {
        // Load payment (read-only, no transaction)
        Payment payment = getPaymentEntityById(paymentId);
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());

        // Validate state transition
        stateMachine.validateTransition(payment.getStatus(), PaymentStatus.PAYMENT_INITIATED);

        // Call gateway (NO database transaction held)
        GatewayPaymentRequest gatewayRequest = GatewayPaymentRequest.builder()
            .internalPaymentId(payment.getPaymentId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .customerEmail(payment.getOrder().getCustomerEmail())
            .build();

        GatewayResponse gatewayResponse = paymentGateway.initiatePayment(gatewayRequest);

        // Persist result in new transaction
        if (gatewayResponse.isSuccess()) {
            return updatePaymentAfterGatewaySuccess(paymentId, PaymentStatus.PAYMENT_INITIATED, 
                gatewayResponse.getGatewayPaymentId(), PaymentEventType.PAYMENT_INITIATED);
        } else {
            return updatePaymentAfterGatewayFailure(paymentId, gatewayResponse.getMessage());
        }
    }

    private Payment getPaymentEntityById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
    }

    @Transactional
    private PaymentResponse updatePaymentAfterGatewaySuccess(
            Long paymentId, PaymentStatus newStatus, String gatewayPaymentId, PaymentEventType eventType) {
        Payment payment = getPaymentEntityById(paymentId);
        
        // Revalidate state (may have changed since gateway call)
        stateMachine.validateTransition(payment.getStatus(), newStatus);
        
        payment.setStatus(newStatus);
        payment.setGatewayPaymentId(gatewayPaymentId);
        Payment updated = paymentRepository.save(payment);
        
        // Create domain event (in same transaction)
        paymentEventService.createPaymentEvent(updated, eventType);
        
        log.info("Payment transitioned: payment_id={}, status={}, gateway_id={}", 
            payment.getPaymentId(), newStatus, gatewayPaymentId);
        
        // Record metrics
        if (newStatus == PaymentStatus.AUTHORIZED) {
            metrics.incrementPaymentsAuthorized();
        } else if (newStatus == PaymentStatus.CAPTURED) {
            metrics.incrementPaymentsCaptured();
        }
        
        return paymentMapper.toResponse(updated);
    }

    @Transactional
    private PaymentResponse updatePaymentAfterGatewayFailure(Long paymentId, String failureReason) {
        Payment payment = getPaymentEntityById(paymentId);
        
        // FAILED is terminal, check if already failed
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment already failed: payment_id={}", payment.getPaymentId());
            return paymentMapper.toResponse(payment);
        }
        
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        Payment updated = paymentRepository.save(payment);
        
        // Create domain event (in same transaction)
        paymentEventService.createPaymentEvent(updated, PaymentEventType.PAYMENT_FAILED);
        
        log.error("Payment failed: payment_id={}, reason={}", payment.getPaymentId(), failureReason);
        metrics.incrementPaymentsFailed();
        
        return paymentMapper.toResponse(updated);
    }

    /**
     * Authorizes a payment.
     * Transitions: PAYMENT_INITIATED -> AUTHORIZED
     * 
     * Transaction Strategy:
     * 1. Load payment
     * 2. Call gateway (NO transaction held)
     * 3. Persist result + event (atomic transaction)
     * 
     * @param paymentId the payment ID
     * @return updated payment response
     */
    public PaymentResponse authorizePayment(Long paymentId) {
        // Load payment
        Payment payment = getPaymentEntityById(paymentId);
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());

        // Validate state transition
        stateMachine.validateTransition(payment.getStatus(), PaymentStatus.AUTHORIZED);

        if (payment.getGatewayPaymentId() == null) {
            throw new BusinessException("GATEWAY_ID_MISSING", "Gateway payment ID is required for authorization");
        }

        // Call gateway (NO database transaction held)
        GatewayResponse gatewayResponse = paymentGateway.authorizePayment(payment.getGatewayPaymentId());

        // Persist result in new transaction
        if (gatewayResponse.isSuccess()) {
            return updatePaymentAfterGatewaySuccess(paymentId, PaymentStatus.AUTHORIZED, 
                payment.getGatewayPaymentId(), PaymentEventType.PAYMENT_AUTHORIZED);
        } else {
            return updatePaymentAfterGatewayFailure(paymentId, gatewayResponse.getMessage());
        }
    }

    /**
     * Captures a payment.
     * Transitions: AUTHORIZED -> CAPTURED
     * 
     * Transaction Strategy:
     * 1. Load payment
     * 2. Call gateway (NO transaction held)
     * 3. Persist result + event (atomic transaction)
     * 
     * @param paymentId the payment ID
     * @return updated payment response
     */
    public PaymentResponse capturePayment(Long paymentId) {
        // Load payment
        Payment payment = getPaymentEntityById(paymentId);
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());

        // Validate state transition
        stateMachine.validateTransition(payment.getStatus(), PaymentStatus.CAPTURED);

        if (payment.getGatewayPaymentId() == null) {
            throw new BusinessException("GATEWAY_ID_MISSING", "Gateway payment ID is required for capture");
        }

        // Call gateway (NO database transaction held)
        GatewayResponse gatewayResponse = paymentGateway.capturePayment(
            payment.getGatewayPaymentId(), payment.getAmount());

        // Persist result in new transaction
        if (gatewayResponse.isSuccess()) {
            return updatePaymentAfterGatewaySuccess(paymentId, PaymentStatus.CAPTURED, 
                payment.getGatewayPaymentId(), PaymentEventType.PAYMENT_CAPTURED);
        } else {
            return updatePaymentAfterGatewayFailure(paymentId, gatewayResponse.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());
        
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByPaymentId(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with paymentId: " + paymentId));
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());
        
        return paymentMapper.toResponse(payment);
    }
}
