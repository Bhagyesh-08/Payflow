# PayFlow Observability Guide

## Overview

PayFlow implements comprehensive observability features for production monitoring, debugging, and operational insights. This guide covers correlation tracing, structured logging, business metrics, and monitoring setup.

## Correlation IDs

### Purpose
Correlation IDs enable request tracing across the entire system, from HTTP entry point through service layers, event publishing, and Kafka consumer processing.

### Implementation

**HTTP Layer** (`CorrelationIdFilter`):
- Accepts incoming `X-Correlation-ID` header from clients
- Generates new UUID if header absent or blank
- Stores in MDC (Mapped Diagnostic Context) for logging
- Returns correlation ID in response header
- Automatically cleans up MDC after request (prevents leakage)

**Usage Example**:
```bash
# Client provides correlation ID
curl -H "X-Correlation-ID: my-trace-123" http://localhost:8080/api/v1/payments/1

# Server generates if missing
curl http://localhost:8080/api/v1/payments/1
# Response includes: X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
```

**Service Layer Integration**:
Services add domain-specific context to MDC:
```java
MDC.put("merchantId", String.valueOf(merchantId));
MDC.put("paymentId", paymentId);
MDC.put("refundId", refundId);
```

**Log Output**:
```
2026-10-07 12:34:56 [http-nio-8080-exec-1] INFO [550e8400-e29b-41d4-a716-446655440000] 
com.payflow.payment.service.PaymentService - Payment created: payment_id=PAY_123, amount=100.00
```

### Boundaries
- **HTTP → Service**: Correlation ID flows through all service method calls
- **Service → Outbox**: EventId serves as correlation for async processing
- **Kafka Consumer**: Separate eventId (correlation across event lifecycle)

Correlation IDs and Event IDs remain distinct concepts. HTTP correlation traces synchronous flows; Event IDs trace asynchronous event processing.

---

## Structured Logging

### Logging Pattern
```
%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{correlationId:-NO-CORRELATION-ID}] %logger{36} - %msg%n
```

### Contextual Fields
PayFlow logs include structured fields for filtering and searching:

| Field | Purpose | Example |
|-------|---------|---------|
| `correlationId` | Request tracing | `550e8400-...` |
| `merchantId` | Merchant isolation | `1` |
| `paymentId` | Payment tracking | `PAY_123` |
| `refundId` | Refund tracking | `REF_456` |
| `disputeId` | Dispute tracking | `DIS_789` |
| `eventId` | Event tracking | `EVT_ABC` |
| `eventType` | Event classification | `PAYMENT_CREATED` |

### Log Levels

**INFO**: Business lifecycle events
```
Payment created: payment_id=PAY_123, amount=100.00
Refund succeeded: refund_id=REF_456, ledger created
Webhook processed successfully: event_id=evt_789, duration_ms=45
```

**WARN**: Recoverable issues, duplicate detection
```
Duplicate webhook event detected: event_id=evt_123
Invalid state transition from CAPTURED to PROCESSING for payment PAY_456
```

**ERROR**: Unrecoverable failures
```
Failed to process event: event_id=evt_123, event_type=PAYMENT_CAPTURED
Payment failed: payment_id=PAY_789, reason=Gateway timeout
```

**DEBUG**: Detailed diagnostics (disabled in production by default)

### Security Rules
The following are **NEVER** logged:
- API keys (`X-API-Key` header)
- Razorpay secrets
- Webhook secrets
- Authorization tokens
- Full request/response bodies containing PII

---

## Business Metrics

### Overview
PayFlow exposes Prometheus-compatible metrics via `/actuator/prometheus` endpoint. All metrics prefixed with `payflow_` for easy discovery.

### Metric Cardinality Strategy
**Low-Cardinality Tags** (Safe):
- `operation`, `status`, `eventType`, `gateway`, `consumer`, `reason`
- Values: bounded, predefined enums

**High-Cardinality IDs** (Avoided):
- `paymentId`, `refundId`, `disputeId`, `correlationId`, `merchantId`
- Would cause metric explosion → performance degradation

### Available Metrics

#### Payment Metrics
```
payflow_payments_created_total          # Counter
payflow_payments_authorized_total       # Counter
payflow_payments_captured_total         # Counter
payflow_payments_failed_total           # Counter
```

**Success Rate Calculation**:
```promql
sum(rate(payflow_payments_captured_total[5m])) / 
(sum(rate(payflow_payments_captured_total[5m])) + sum(rate(payflow_payments_failed_total[5m]))) * 100
```

#### Refund Metrics
```
payflow_refunds_requested_total         # Counter
payflow_refunds_succeeded_total         # Counter
payflow_refunds_failed_total            # Counter
payflow_refund_gateway_latency          # Histogram (ms)
```

**Latency Percentiles**:
```promql
histogram_quantile(0.95, rate(payflow_refund_gateway_latency_bucket[5m]))  # p95
histogram_quantile(0.99, rate(payflow_refund_gateway_latency_bucket[5m]))  # p99
```

#### Dispute Metrics
```
payflow_disputes_created_total          # Counter
payflow_disputes_won_total              # Counter
payflow_disputes_lost_total             # Counter
```

#### Webhook Metrics
```
payflow_webhooks_received_total{eventType}           # Counter with tag
payflow_webhooks_rejected_total{reason}              # Counter with tag
payflow_webhooks_duplicate_total                     # Counter
payflow_webhooks_processing_errors_total             # Counter
payflow_webhook_processing_latency                   # Histogram (ms)
```

**Tags**:
- `eventType`: `payment`, `refund`, `dispute` (sanitized from full event type)
- `reason`: `signature_verification`, `duplicate`, `parse_error`, `other`

#### Event/Outbox Metrics
```
payflow_outbox_events_created_total{eventType}       # Counter
payflow_outbox_events_published_total{eventType}     # Counter
payflow_outbox_events_failed_total{eventType}        # Counter
payflow_events_consumed_total{eventType}             # Counter
payflow_duplicate_events_total{consumer}             # Counter
payflow_event_processing_errors_total{consumer}      # Counter
payflow_event_processing_latency{consumer}           # Histogram (ms)
```

#### Ledger Metrics
```
payflow_ledger_transactions_created_total            # Counter
payflow_ledger_transactions_failed_total             # Counter
```

#### Reconciliation Metrics
```
payflow_reconciliation_runs_total                    # Counter
payflow_reconciliation_matched_total                 # Counter
payflow_reconciliation_discrepancies_total           # Counter
payflow_reconciliation_duration                      # Histogram (ms)
```

---

## Monitoring Setup

### Prometheus Configuration

**Scrape Configuration** (`prometheus.yml`):
```yaml
scrape_configs:
  - job_name: 'payflow-application'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'payflow'
          environment: 'development'
```

**Start Prometheus**:
```bash
docker-compose up -d prometheus
# Access: http://localhost:9090
```

**Example Queries**:
```promql
# Payment success rate over 5 minutes
rate(payflow_payments_captured_total[5m])

# Webhook processing p95 latency
histogram_quantile(0.95, rate(payflow_webhook_processing_latency_bucket[5m]))

# Event consumer duplicate detection rate
rate(payflow_duplicate_events_total[5m])
```

### Grafana Dashboard

**Start Grafana**:
```bash
docker-compose up -d grafana
# Access: http://localhost:3000
# Default credentials: admin/admin
```

**Pre-configured Dashboard**: Automatically provisioned via `/grafana/provisioning/dashboards/payflow-dashboard.json`

**Dashboard Panels**:
1. **Payment Operations**: Line graph showing created/authorized/captured/failed rates
2. **Payment Success Rate**: Gauge with thresholds (red < 90%, yellow < 95%, green ≥ 95%)
3. **Refund Operations**: Line graph showing requested/succeeded/failed rates
4. **Refund Gateway Latency**: p95 latency over time
5. **Webhook Processing**: Received/rejected/duplicate/error rates by event type
6. **Webhook Latency**: p50/p95/p99 latencies
7. **Dispute Operations**: Created/won/lost counts
8. **Outbox & Events**: Created/published/failed event rates
9. **Event Duplicates**: Duplicate detection by consumer
10. **Ledger Transactions**: Created/failed counts
11. **Reconciliation**: Runs/matched/discrepancies

---

## Alert Recommendations

### Critical Alerts
```yaml
- alert: PaymentFailureRateHigh
  expr: rate(payflow_payments_failed_total[5m]) > 0.1
  for: 5m
  annotations:
    summary: "Payment failure rate above 10%"

- alert: WebhookProcessingErrors
  expr: rate(payflow_webhooks_processing_errors_total[5m]) > 0.05
  for: 5m
  annotations:
    summary: "Webhook processing errors above threshold"

- alert: OutboxPublishingFailed
  expr: rate(payflow_outbox_events_failed_total[5m]) > 0
  for: 10m
  annotations:
    summary: "Outbox events failing to publish to Kafka"

- alert: LedgerTransactionFailures
  expr: increase(payflow_ledger_transactions_failed_total[5m]) > 0
  for: 1m
  annotations:
    summary: "Ledger transaction failures detected"
    description: "Financial integrity issue - investigate immediately"
```

### Warning Alerts
```yaml
- alert: HighWebhookLatency
  expr: histogram_quantile(0.95, rate(payflow_webhook_processing_latency_bucket[5m])) > 1000
  for: 10m
  annotations:
    summary: "Webhook p95 latency above 1 second"

- alert: ReconciliationDiscrepancies
  expr: increase(payflow_reconciliation_discrepancies_total[1h]) > 10
  for: 5m
  annotations:
    summary: "Reconciliation discrepancies detected"
```

---

## Operational Runbooks

### Debugging Payment Failures

1. **Find correlation ID** from client or logs
2. **Search logs** for correlation ID: `grep "550e8400-..." application.log`
3. **Check metrics**: `payflow_payments_failed_total` by time range
4. **Identify failure pattern**: Gateway timeout? State machine rejection?
5. **Trace through layers**: Payment creation → Gateway call → Webhook reception

### Investigating Webhook Issues

1. **Check rejection reason**: `payflow_webhooks_rejected_total{reason="signature_verification"}`
2. **Verify webhook secret** configuration
3. **Search logs** for `event_id` from webhook payload
4. **Check duplicate detection**: Look for "Duplicate webhook event detected" logs
5. **Validate state transitions**: Ensure webhook events arrive in expected order

### Monitoring Event Processing

1. **Check consumer lag**: Kafka consumer offset monitoring (external)
2. **Duplicate rate**: `payflow_duplicate_events_total{consumer="PaymentEventConsumer"}`
3. **Processing errors**: `payflow_event_processing_errors_total`
4. **Latency**: `payflow_event_processing_latency` percentiles
5. **Trace event**: Search logs for `event_id` to see complete processing flow

---

## Health Checks

**Endpoint**: `http://localhost:8080/actuator/health`

**Response**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

**Health Status**:
- `UP`: Service healthy, all dependencies available
- `DOWN`: Service or dependency unavailable

**Kubernetes Liveness/Readiness**: Use `/actuator/health` endpoint

---

## Production Checklist

### Before Deployment
- [ ] Verify correlation IDs propagate correctly
- [ ] Test metrics endpoint: `curl http://localhost:8080/actuator/prometheus`
- [ ] Validate log output contains contextual fields
- [ ] Confirm no sensitive data in logs
- [ ] Review Grafana dashboard displays correctly
- [ ] Set up Prometheus alerts in production

### Monitoring
- [ ] Payment success rate > 95%
- [ ] Webhook processing latency p95 < 500ms
- [ ] No ledger transaction failures
- [ ] Event consumer lag < 1 minute
- [ ] Reconciliation discrepancy rate < 1%

### Logging
- [ ] Centralized log aggregation (ELK, Splunk, CloudWatch)
- [ ] Log retention policy configured
- [ ] Alert on ERROR-level log rate spike
- [ ] Correlation ID indexed for fast searches

---

## Performance Considerations

### Metrics Overhead
- **Minimal**: Counter increments are O(1) operations
- **Histogram**: Slightly higher overhead for latency measurements
- **Recommendation**: Keep metric count reasonable (< 1000 unique time series)

### Logging Overhead
- **MDC**: Negligible overhead (ThreadLocal access)
- **Structured fields**: Use selectively in hot paths
- **Recommendation**: DEBUG level disabled in production

### Correlation ID
- **UUID generation**: ~1μs per request
- **MDC cleanup**: Automatic via filter's finally block
- **Thread safety**: MDC is ThreadLocal (no contention)

---

## Future Enhancements

### Tracing (Not Implemented)
- **OpenTelemetry**: Distributed tracing with spans
- **Jaeger/Zipkin**: Visual trace timelines
- **Trace context propagation**: W3C Trace Context standard

### Advanced Metrics (Not Implemented)
- **Micrometer tags**: Merchant-specific metrics (with cardinality limits)
- **Custom histograms**: Bucket customization for domain-specific SLOs
- **Exemplars**: Link metrics to trace IDs

### Alerting (Not Implemented)
- **Alertmanager**: Prometheus alert routing
- **PagerDuty/Opsgenie**: On-call escalation
- **Slack/Email**: Notification channels

---

## Troubleshooting

### Metrics Not Appearing
1. Check `/actuator/prometheus` endpoint returns data
2. Verify Prometheus scraping: Check Prometheus UI → Targets
3. Confirm application is generating traffic (metrics won't exist with 0 count)
4. Validate Micrometer dependency in pom.xml

### Correlation ID Not in Logs
1. Verify `CorrelationIdFilter` is registered (Spring auto-configuration)
2. Check log pattern includes `%X{correlationId:-NO-CORRELATION-ID}`
3. Ensure MDC cleanup isn't happening prematurely
4. Test with explicit header: `curl -H "X-Correlation-ID: test-123"`

### High Metric Cardinality
1. **Symptom**: Prometheus memory usage growing
2. **Cause**: Unbounded tag values (IDs, dynamic strings)
3. **Fix**: Remove high-cardinality tags, use sanitization
4. **Check**: Prometheus `/api/v1/label/__name__/values` to see all metrics

---

## Related Documentation
- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture
- [PRODUCTION_READINESS_AUDIT.md](PRODUCTION_READINESS_AUDIT.md) - Quality audit results
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Documentation](https://grafana.com/docs/)
