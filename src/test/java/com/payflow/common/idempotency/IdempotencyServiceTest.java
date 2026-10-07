package com.payflow.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.enums.MerchantStatus;
import com.payflow.payment.dto.CreatePaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private ObjectMapper objectMapper;
    private Merchant testMerchant;
    private CreatePaymentRequest testRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(redisTemplate, idempotencyRepository, objectMapper);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        testMerchant = Merchant.builder()
                .id(1L)
                .businessName("Test Merchant")
                .email("test@merchant.com")
                .merchantCode("MER_TEST")
                .status(MerchantStatus.ACTIVE)
                .build();

        testRequest = CreatePaymentRequest.builder()
                .orderId(1L)
                .paymentMethod("UPI")
                .build();
    }

    @Test
    void checkIdempotency_NoCache_ReturnsNull() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);
        when(idempotencyRepository.findByMerchantIdAndIdempotencyKey(anyLong(), anyString()))
            .thenReturn(Optional.empty());

        // Act
        IdempotencyCachedResponse result = idempotencyService.checkIdempotency(
            testMerchant.getId(), "test-key", testRequest);

        // Assert
        assertThat(result).isNull();
        verify(valueOperations).get(anyString());
        verify(idempotencyRepository).findByMerchantIdAndIdempotencyKey(testMerchant.getId(), "test-key");
    }

    @Test
    void checkIdempotency_RedisCacheHit_ReturnsCachedResponse() throws Exception {
        // Arrange
        IdempotencyCachedResponse cachedResponse = IdempotencyCachedResponse.builder()
                .responseStatus(201)
                .responseBody("{\"paymentId\":\"PAY_123\"}")
                .requestHash(generateRequestHash(testRequest))
                .paymentId(1L)
                .build();

        String cachedJson = objectMapper.writeValueAsString(cachedResponse);
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        // Act
        IdempotencyCachedResponse result = idempotencyService.checkIdempotency(
            testMerchant.getId(), "test-key", testRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getResponseStatus()).isEqualTo(201);
        assertThat(result.getPaymentId()).isEqualTo(1L);
        verify(valueOperations).get(anyString());
        verify(idempotencyRepository, never()).findByMerchantIdAndIdempotencyKey(anyLong(), anyString());
    }

    @Test
    void checkIdempotency_RedisMiss_DatabaseHit_ReturnsCachedResponse() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);

        IdempotencyRecord dbRecord = IdempotencyRecord.builder()
                .id(1L)
                .merchant(testMerchant)
                .idempotencyKey("test-key")
                .requestHash(generateRequestHash(testRequest))
                .responseStatus(201)
                .responseBody("{\"paymentId\":\"PAY_123\"}")
                .build();

        when(idempotencyRepository.findByMerchantIdAndIdempotencyKey(testMerchant.getId(), "test-key"))
            .thenReturn(Optional.of(dbRecord));

        // Act
        IdempotencyCachedResponse result = idempotencyService.checkIdempotency(
            testMerchant.getId(), "test-key", testRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getResponseStatus()).isEqualTo(201);
        verify(idempotencyRepository).findByMerchantIdAndIdempotencyKey(testMerchant.getId(), "test-key");
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void checkIdempotency_SameKeyDifferentRequest_ThrowsException() throws Exception {
        // Arrange
        CreatePaymentRequest differentRequest = CreatePaymentRequest.builder()
                .orderId(2L) // Different order ID
                .paymentMethod("CARD")
                .build();

        IdempotencyCachedResponse cachedResponse = IdempotencyCachedResponse.builder()
                .responseStatus(201)
                .responseBody("{\"paymentId\":\"PAY_123\"}")
                .requestHash(generateRequestHash(testRequest)) // Hash from original request
                .paymentId(1L)
                .build();

        String cachedJson = objectMapper.writeValueAsString(cachedResponse);
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        // Act & Assert
        assertThatThrownBy(() ->
            idempotencyService.checkIdempotency(testMerchant.getId(), "test-key", differentRequest)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("different request parameters");
    }

    @Test
    void storeIdempotencyRecord_Success() {
        // Arrange
        when(idempotencyRepository.save(any(IdempotencyRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        idempotencyService.storeIdempotencyRecord(
            testMerchant, "test-key", testRequest, null, 201, "{\"paymentId\":\"PAY_123\"}");

        // Assert
        verify(idempotencyRepository).save(any(IdempotencyRecord.class));
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
    }

    private String generateRequestHash(CreatePaymentRequest request) {
        try {
            // Use same deterministic hashing as the service
            ObjectMapper deterministicMapper = new ObjectMapper();
            deterministicMapper.configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
            deterministicMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            
            String json = deterministicMapper.writeValueAsString(request);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request in test", e);
        }
    }
}
