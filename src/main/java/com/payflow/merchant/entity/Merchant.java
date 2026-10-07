package com.payflow.merchant.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.merchant.enums.MerchantStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Merchant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String businessName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, unique = true, length = 50)
    private String merchantCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String apiKey;
}
