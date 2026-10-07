package com.payflow.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency Service
 * 
 * Provides two-layer idempotency protection:
 * 1. Redis: Fast in-memory cache with TTL for quick duplicate detection
 * 2. Database: Persistent storage with unique constraint as final safety net
 * 
 * Design decisions:
 * - Redis is used for performance (avoids DB round-trips on duplicates)
 * - Database unique constraint ensures safety even if Redis is unavailable
 * - Idempotency is scoped per merchant (same key for different merchants is allowed)
 * - Request hash detects conflicting reuse (same key, different request body)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "payflow:idempotency";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    /**
     * Checks if an idempotency key has been used and returns cached response if available.
     * 
     * @param merchantId the merchant ID
     * @param idempotencyKey the idempotency key
     * @param requestBody the request body to hash
     * @return cached response if exists, null otherwise
     */
    public IdempotencyCachedResponse checkIdempotency(Long merchantId, String idempotencyKey, Object requestBody) {
        String redisKey = buildRedisKey(merchantId, idempotencyKey);
        String requestHash = hashRequest(requestBody);

        // Try Redis first (fast path)
        String cachedValue = redisTemplate.opsForValue().get(redisKey);
        if (cachedValue != null) {
            log.info("Idempotency hit in Redis for key: {}", idempotencyKey);
            try {
                IdempotencyCachedResponse cached = objectMapper.readValue(cachedValue, IdempotencyCachedResponse.class);
                
                // Verify request hash matches (detect conflicting reuse)
                if (!requestHash.equals(cached.getRequestHash())) {
                    throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key has been reused with different request parameters");
                }
                
                return cached;
            } catch (BusinessException e) {
                // Re-throw business logic exceptions (like hash conflicts)
                throw e;
            } catch (Exception e) {
                log.error("Failed to deserialize cached idempotency response", e);
                // Fall through to database check for other errors (JSON parsing, etc.)
            }
        }

        // Check database (persistent layer)
        return idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
            .map(record -> {
                log.info("Idempotency hit in database for key: {}", idempotencyKey);
                
                // Verify request hash matches
                if (!requestHash.equals(record.getRequestHash())) {
                    throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key has been reused with different request parameters");
                }
                
                IdempotencyCachedResponse response = IdempotencyCachedResponse.builder()
                    .responseStatus(record.getResponseStatus())
                    .responseBody(record.getResponseBody())
                    .requestHash(record.getRequestHash())
                    .paymentId(record.getPayment() != null ? record.getPayment().getId() : null)
                    .build();
                
                // Populate Redis cache from database
                cacheResponse(redisKey, response);
                
                return response;
            })
            .orElse(null);
    }

    /**
     * Stores idempotency record in both Redis and database.
     * Uses REQUIRES_NEW to ensure idempotency record is persisted even if outer transaction fails.
     * 
     * @param merchant the merchant
     * @param idempotencyKey the idempotency key
     * @param requestBody the request body
     * @param payment the created payment
     * @param responseStatus HTTP status code
     * @param responseBody the response body as string
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeIdempotencyRecord(Merchant merchant, String idempotencyKey, Object requestBody,
                                       Payment payment, int responseStatus, String responseBody) {
        String redisKey = buildRedisKey(merchant.getId(), idempotencyKey);
        String requestHash = hashRequest(requestBody);

        try {
            // Store in database first (source of truth)
            IdempotencyRecord record = IdempotencyRecord.builder()
                .merchant(merchant)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .payment(payment)
                .responseStatus(responseStatus)
                .responseBody(responseBody)
                .build();
            
            idempotencyRepository.save(record);
            log.info("Stored idempotency record in database for key: {}", idempotencyKey);

            // Cache in Redis for fast access
            IdempotencyCachedResponse cachedResponse = IdempotencyCachedResponse.builder()
                .responseStatus(responseStatus)
                .responseBody(responseBody)
                .requestHash(requestHash)
                .paymentId(payment != null ? payment.getId() : null)
                .build();
            
            cacheResponse(redisKey, cachedResponse);
            
        } catch (Exception e) {
            log.error("Failed to store idempotency record for key: {}", idempotencyKey, e);
            // Don't throw - idempotency storage failure shouldn't fail the main operation
            // The unique constraint in DB will still protect against duplicates
        }
    }

    private void cacheResponse(String redisKey, IdempotencyCachedResponse response) {
        try {
            String jsonValue = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, jsonValue, REDIS_TTL.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Cached idempotency response in Redis: {}", redisKey);
        } catch (Exception e) {
            log.error("Failed to cache idempotency response in Redis", e);
            // Continue - Redis caching is optional
        }
    }

    private String buildRedisKey(Long merchantId, String idempotencyKey) {
        return String.format("%s:%d:%s", REDIS_KEY_PREFIX, merchantId, idempotencyKey);
    }

    /**
     * Hashes the request body for conflict detection.
     * Uses SHA-256 for cryptographic strength.
     * Ensures deterministic serialization by sorting JSON keys.
     */
    private String hashRequest(Object requestBody) {
        try {
            // Configure ObjectMapper for deterministic serialization
            ObjectMapper deterministicMapper = new ObjectMapper();
            deterministicMapper.configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
            deterministicMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            
            String json = deterministicMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
