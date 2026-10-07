package com.payflow.reconciliation.mapper;

import com.payflow.reconciliation.dto.ReconciliationRecordResponse;
import com.payflow.reconciliation.dto.ReconciliationRunResponse;
import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.entity.ReconciliationRun;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationMapper {

    public ReconciliationRunResponse toRunResponse(ReconciliationRun run) {
        return ReconciliationRunResponse.builder()
            .id(run.getId())
            .merchantId(run.getMerchantId())
            .gateway(run.getGateway())
            .businessDate(run.getBusinessDate())
            .startedAt(run.getStartedAt())
            .completedAt(run.getCompletedAt())
            .status(run.getStatus().name())
            .totalRecords(run.getTotalRecords())
            .matchedRecords(run.getMatchedRecords())
            .discrepancyRecords(run.getDiscrepancyRecords())
            .unresolvedRecords(run.getUnresolvedRecords())
            .errorMessage(run.getErrorMessage())
            .build();
    }

    public ReconciliationRecordResponse toRecordResponse(ReconciliationRecord record) {
        return ReconciliationRecordResponse.builder()
            .id(record.getId())
            .runId(record.getReconciliationRun().getId())
            .gateway(record.getGateway())
            .gatewayReference(record.getGatewayReference())
            .paymentId(record.getPaymentId())
            .internalAmount(record.getInternalAmount())
            .gatewayAmount(record.getGatewayAmount())
            .currency(record.getCurrency())
            .internalStatus(record.getInternalStatus())
            .gatewayStatus(record.getGatewayStatus())
            .result(record.getResult().name())
            .discrepancyType(record.getDiscrepancyType().name())
            .reconciledAt(record.getReconciledAt())
            .notes(record.getNotes())
            .build();
    }
}
