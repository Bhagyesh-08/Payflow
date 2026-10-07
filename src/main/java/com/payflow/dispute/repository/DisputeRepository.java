package com.payflow.dispute.repository;

import com.payflow.dispute.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    
    Optional<Dispute> findByDisputeId(String disputeId);
    
    Optional<Dispute> findByGatewayDisputeId(String gatewayDisputeId);
    
    List<Dispute> findByPaymentId(Long paymentId);
    
    List<Dispute> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
