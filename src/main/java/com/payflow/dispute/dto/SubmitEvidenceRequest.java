package com.payflow.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitEvidenceRequest {
    
    @NotBlank(message = "Evidence description is required")
    private String evidenceDescription;
}
