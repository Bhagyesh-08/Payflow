package com.payflow.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);
    
    @Query("SELECT COUNT(i) FROM IdempotencyRecord i WHERE i.merchant.id = :merchantId AND i.idempotencyKey = :idempotencyKey")
    long countByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);
    
    // Cleanup method for expired records (to be called by scheduled job in future)
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
