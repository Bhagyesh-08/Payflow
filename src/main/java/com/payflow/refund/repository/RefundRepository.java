package com.payflow.refund.repository;

import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    
    Optional<Refund> findByRefundId(String refundId);
    
    Optional<Refund> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);
    
    List<Refund> findByPaymentId(Long paymentId);
    
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.payment.id = :paymentId AND r.status = :status")
    BigDecimal sumSuccessfulRefundsByPaymentId(@Param("paymentId") Long paymentId, @Param("status") RefundStatus status);
}
