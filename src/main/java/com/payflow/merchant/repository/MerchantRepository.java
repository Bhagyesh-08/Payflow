package com.payflow.merchant.repository;

import com.payflow.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByMerchantCode(String merchantCode);
    Optional<Merchant> findByEmail(String email);
    Optional<Merchant> findByApiKey(String apiKey);
    boolean existsByEmail(String email);
    boolean existsByBusinessName(String businessName);
}
