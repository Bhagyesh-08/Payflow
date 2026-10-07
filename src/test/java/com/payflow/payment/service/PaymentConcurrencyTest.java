package com.payflow.payment.service;

import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.enums.MerchantStatus;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.order.entity.Order;
import com.payflow.order.enums.OrderStatus;
import com.payflow.order.repository.OrderRepository;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency Test for Payment Creation with Idempotency
 * 
 * This test proves that:
 * 1. 100 concurrent requests with same idempotency key create exactly 1 payment
 * 2. Database unique constraint prevents duplicates even under high concurrency
 * 3. Redis + Database layer work together to handle concurrent requests safely
 */
@SpringBootTest
@Testcontainers
class PaymentConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payflow_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Merchant testMerchant;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Clean up
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        // Create test merchant
        testMerchant = Merchant.builder()
                .businessName("Concurrency Test Merchant")
                .email("concurrency@test.com")
                .merchantCode("MER_CONCURRENT")
                .status(MerchantStatus.ACTIVE)
                .apiKey("test_key")
                .build();
        testMerchant = merchantRepository.save(testMerchant);

        // Create test order
        testOrder = Order.builder()
                .orderNumber("ORD_CONCURRENT_001")
                .merchant(testMerchant)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .status(OrderStatus.CREATED)
                .customerEmail("customer@test.com")
                .build();
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    void testConcurrentPaymentCreation_SameIdempotencyKey_CreatesExactlyOnePayment() throws Exception {
        // Arrange
        int numberOfThreads = 100;
        String idempotencyKey = "CONCURRENT_TEST_KEY_" + System.currentTimeMillis();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(testOrder.getId())
                .paymentMethod("UPI")
                .build();

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> createdPaymentIds = Collections.synchronizedList(new ArrayList<>());

        // Act - Submit 100 concurrent payment creation requests
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Attempt to create payment
                    PaymentResponse response = paymentService.createPayment(
                        request, testMerchant.getId(), idempotencyKey);
                    
                    successCount.incrementAndGet();
                    createdPaymentIds.add(response.getId());
                    
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("IDEMPOTENCY")) {
                        duplicateCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        System.err.println("Unexpected error: " + e.getMessage());
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release all threads at once to maximize concurrency
        startLatch.countDown();
        
        // Wait for all threads to complete (timeout after 30 seconds)
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert
        assertThat(completed).isTrue();
        
        // Exactly 1 payment should be created in database
        long paymentCount = paymentRepository.count();
        assertThat(paymentCount)
            .withFailMessage("Expected exactly 1 payment, but found %d", paymentCount)
            .isEqualTo(1);

        // All created payment IDs should be the same
        assertThat(createdPaymentIds)
            .withFailMessage("Multiple different payment IDs were returned: %s", createdPaymentIds)
            .allMatch(id -> id.equals(createdPaymentIds.get(0)));

        // At least 1 thread should succeed (the first one)
        assertThat(successCount.get())
            .withFailMessage("At least 1 thread should succeed, but got %d", successCount.get())
            .isGreaterThanOrEqualTo(1);

        // Most threads should return cached response (total should be 100)
        int totalProcessed = successCount.get() + duplicateCount.get() + errorCount.get();
        assertThat(totalProcessed)
            .withFailMessage("Expected all 100 threads to complete, but only %d did", totalProcessed)
            .isEqualTo(numberOfThreads);

        // No unexpected errors
        assertThat(errorCount.get())
            .withFailMessage("Unexpected errors occurred: %d", errorCount.get())
            .isEqualTo(0);

        System.out.println("=== Concurrency Test Results ===");
        System.out.println("Total threads: " + numberOfThreads);
        System.out.println("Success count: " + successCount.get());
        System.out.println("Duplicate/cached count: " + duplicateCount.get());
        System.out.println("Error count: " + errorCount.get());
        System.out.println("Payments in DB: " + paymentCount);
        System.out.println("Test PASSED: Exactly 1 payment created from 100 concurrent requests ✓");
    }

    @Test
    void testConcurrentPaymentCreation_DifferentIdempotencyKeys_CreatesMultiplePayments() throws Exception {
        // Arrange
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Submit concurrent requests with DIFFERENT idempotency keys
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNum = i;
            
            // Create unique order for each thread
            Order uniqueOrder = Order.builder()
                    .orderNumber("ORD_CONCURRENT_" + threadNum)
                    .merchant(testMerchant)
                    .amount(new BigDecimal("500.00"))
                    .currency("INR")
                    .status(OrderStatus.CREATED)
                    .build();
            uniqueOrder = orderRepository.save(uniqueOrder);
            
            final Long orderId = uniqueOrder.getId();
            
            executorService.submit(() -> {
                try {
                    CreatePaymentRequest request = CreatePaymentRequest.builder()
                            .orderId(orderId)
                            .paymentMethod("CARD")
                            .build();
                    
                    String uniqueIdempotencyKey = "UNIQUE_KEY_" + threadNum;
                    
                    paymentService.createPayment(request, testMerchant.getId(), uniqueIdempotencyKey);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    System.err.println("Thread " + threadNum + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numberOfThreads);
        
        // Should have created multiple payments (1 per unique idempotency key)
        long paymentCount = paymentRepository.count();
        assertThat(paymentCount).isGreaterThanOrEqualTo(numberOfThreads);
        
        System.out.println("=== Different Keys Test Results ===");
        System.out.println("Threads with unique keys: " + numberOfThreads);
        System.out.println("Payments created: " + paymentCount);
        System.out.println("Test PASSED: Multiple payments created with different keys ✓");
    }
}
