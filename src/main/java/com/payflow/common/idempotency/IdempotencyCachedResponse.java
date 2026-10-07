package com.payflow.common.idempotency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyCachedResponse {
    private Integer responseStatus;
    private String responseBody;
    private String requestHash;
    private Long paymentId;
}
