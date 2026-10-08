package com.payflow.common.ratelimit;

import java.time.Duration;

/**
 * Rate Limiting Service Interface.
 * 
 * DESIGN DECISION: Interface-only boundary.
 * 
 * PayFlow defines the contract for rate limiting but DOES NOT implement
 * distributed rate limiting within this monolith. Rate limiting should be
 * implemented at the infrastructure layer (API Gateway, Reverse Proxy, or
 * separate Distributed Rate Limiter service).
 * 
 * Why Not Implemented Here:
 * 1. Distributed rate limiting requires shared state (Redis, etc.)
 * 2. Belongs at infrastructure layer (before requests hit application)
 * 3. Better handled by API Gateway (AWS ALB, Kong, NGINX, etc.)
 * 4. Simpler rate limiters (in-memory) don't work in multi-instance deployments
 * 
 * Recommended Implementation Options:
 * 
 * Option 1: API Gateway Rate Limiting
 * - AWS API Gateway: Built-in throttling (burst, sustained limits)
 * - Kong Gateway: rate-limiting plugin with Redis backend
 * - NGINX: limit_req module with shared memory zone
 * 
 * Option 2: Separate Rate Limiter Service (Recommended for Portfolio)
 * - Standalone distributed rate limiter using Token Bucket or Sliding Window
 * - Redis-backed for low-latency lookups
 * - Demonstrates distributed systems knowledge
 * - Can be referenced as separate GitHub project: "Distributed-Rate-Limiter"
 * 
 * Option 3: Application-Level (If Really Needed)
 * - Use bucket4j library with Redis backend
 * - Add @RateLimit annotation for declarative rate limiting
 * - Filter checks limits before controller execution
 * 
 * Rate Limiting Strategy (When Implemented):
 * - Merchant-level limits: 1000 requests/minute per merchant
 * - Endpoint-specific: Payment creation 100/min, queries 1000/min
 * - IP-based fallback for unauthenticated endpoints
 * - Graceful degradation: Return 429 Too Many Requests with Retry-After header
 * 
 * Example Implementation (Not Included):
 * 
 * public interface RateLimitService {
 *     boolean tryAcquire(String key, int permits);
 *     boolean tryAcquire(String key, int permits, Duration window);
 *     RateLimitResult checkLimit(String merchantId, String endpoint);
 * }
 * 
 * Response on Rate Limit Exceeded:
 * HTTP 429 Too Many Requests
 * {
 *   "code": "RATE_LIMIT_EXCEEDED",
 *   "message": "Rate limit exceeded for merchant. Try again in 30 seconds.",
 *   "retryAfter": 30
 * }
 * 
 * @see <a href="https://github.com/Bhagyesh-08/Distributed-Rate-Limiter">Distributed Rate Limiter Project</a>
 */
public interface RateLimitService {

    /**
     * Checks if the request is allowed under current rate limits.
     * 
     * @param merchantId Merchant identifier
     * @param endpoint Endpoint being accessed
     * @return true if request allowed, false if rate limit exceeded
     */
    boolean isAllowed(String merchantId, String endpoint);

    /**
     * Records a request for rate limit tracking.
     * 
     * @param merchantId Merchant identifier
     * @param endpoint Endpoint being accessed
     */
    void recordRequest(String merchantId, String endpoint);

    /**
     * Gets remaining quota for merchant.
     * 
     * @param merchantId Merchant identifier
     * @param endpoint Endpoint being accessed
     * @return Remaining requests in current window
     */
    int getRemainingQuota(String merchantId, String endpoint);
}
