# PayFlow - Payment Processing Platform

A production-grade payment processing and reconciliation platform built with **Java 21** and **Spring Boot 3**, demonstrating enterprise-level backend architecture, event-driven design, and financial transaction management.

## 🎯 Project Overview

PayFlow is a comprehensive payment gateway integration system that handles the complete payment lifecycle including payments, refunds, disputes, and financial reconciliation. The platform showcases modern backend development practices with a focus on reliability, consistency, and operational observability.

## 🏗️ Architecture Highlights

- **Modular Monolith**: Clean separation of concerns across payment, refund, dispute, ledger, and reconciliation domains
- **Event-Driven Architecture**: Transactional outbox pattern with Kafka for reliable event processing
- **State Machine Pattern**: Enforced state transitions for payments, refunds, and disputes
- **Double-Entry Accounting**: Immutable ledger system with compensating transactions
- **Gateway Abstraction**: Clean integration layer supporting multiple payment providers (Razorpay implemented)
- **Idempotency**: Multi-layer duplicate prevention (Redis + Database) for payment operations
- **Security**: API key authentication with merchant-level authorization
- **Observability**: Structured logging, correlation IDs, and Prometheus metrics

## 🛠️ Technology Stack

- **Runtime**: Java 21, Spring Boot 3.2.5
- **Database**: PostgreSQL with Flyway migrations
- **Cache**: Redis (Lettuce client)
- **Messaging**: Apache Kafka
- **Security**: Spring Security
- **Monitoring**: Micrometer + Prometheus, Actuator
- **Build Tool**: Maven
- **Testing**: JUnit 5, Mockito, Spring Test

## 📁 Project Structure

```
src/main/java/com/payflow/
├── common/              # Shared utilities, security, observability
├── payment/             # Payment domain (entities, services, state machine)
├── refund/              # Refund domain with gateway execution
├── dispute/             # Chargeback and dispute management
├── ledger/              # Double-entry accounting system
├── reconciliation/      # Financial reconciliation engine
├── event/               # Event-driven components (outbox, consumers)
├── webhook/             # Webhook verification and processing
├── infrastructure/      # Gateway integrations (Razorpay)
├── merchant/            # Merchant management
└── order/               # Order management
```

## ✨ Key Features

### Payment Processing
- Full payment lifecycle: CREATED → INITIATED → AUTHORIZED → CAPTURED/FAILED
- Idempotent payment creation with Redis + DB constraint
- Gateway calls isolated from database transactions
- Optimistic locking for concurrent updates

### Refund Management
- Partial and full refund support
- Gateway-integrated refund execution
- Refund validation against captured amounts
- State machine enforcement (REQUESTED → PROCESSING → SUCCEEDED/FAILED)

### Dispute Handling
- Complete dispute lifecycle (OPEN → EVIDENCE_REQUIRED → WON/LOST/CLOSED)
- Evidence submission workflow
- Automated ledger adjustments for dispute outcomes
- Webhook-driven status updates

### Financial Ledger
- Immutable double-entry accounting
- Database-enforced immutability (PostgreSQL triggers)
- Compensating transactions for refunds and disputes
- Balance validation (debits = credits)

### Event-Driven Integration
- Transactional outbox pattern for reliable event publishing
- Idempotent Kafka consumers with processed-event tracking
- At-least-once delivery with duplicate detection
- Event ordering via partition keys

### Reconciliation
- Automated matching between internal records and gateway data
- Discrepancy detection and reporting
- Idempotent reconciliation runs
- Multiple matching strategies

### Security & Observability
- API key-based authentication
- Merchant-level authorization on all operations
- HMAC-SHA256 webhook signature verification
- Structured logging with correlation IDs (MDC)
- Prometheus business metrics (payments, refunds, disputes, webhooks)
- Health checks for dependencies (PostgreSQL, Redis, Kafka)

## 🚀 Getting Started

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.8+

### Quick Start

1. **Clone the repository**
```bash
git clone https://github.com/Bhagyesh-08/PayFlow.git
cd PayFlow
```

2. **Start infrastructure services**
```bash
docker-compose up -d
```

3. **Build the application**
```bash
mvn clean install
```

4. **Run the application**
```bash
mvn spring-boot:run
```

5. **Access endpoints**
- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

See [QUICK_START.md](QUICK_START.md) for detailed setup instructions and API examples.

## 📊 Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PaymentServiceTest

# Run with coverage
mvn clean verify
```

**Test Coverage**: 160/160 unit tests passing (integration tests require Docker environment)

## 📖 Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Detailed architecture, design decisions, and patterns
- **[QUICK_START.md](QUICK_START.md)** - Setup instructions and API usage examples
- **[LEDGER.md](LEDGER.md)** - Double-entry accounting system design
- **[RECONCILIATION.md](RECONCILIATION.md)** - Financial reconciliation process
- **[PRODUCTION_READINESS_AUDIT.md](PRODUCTION_READINESS_AUDIT.md)** - Comprehensive system audit and quality assessment

## 🔑 Configuration

Key application properties:

```yaml
# Database
spring.datasource.url=jdbc:postgresql://localhost:5433/payflow

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Razorpay (set as environment variables)
razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}
razorpay.webhook-secret=${RAZORPAY_WEBHOOK_SECRET}
```

## 🎓 Learning Outcomes

This project demonstrates:
- **Enterprise Java Development**: Spring Boot best practices, dependency injection, transactional boundaries
- **Distributed Systems**: Event-driven architecture, idempotency, eventual consistency
- **Financial Systems**: Double-entry accounting, reconciliation, state machines
- **Payment Integration**: Gateway abstraction, webhook processing, signature verification
- **Production Engineering**: Observability, metrics, correlation tracing, error handling
- **Data Integrity**: Optimistic locking, unique constraints, immutable ledgers
- **Testing**: Unit testing, mocking, test-driven development

## 🤝 Contributing

This is a portfolio/learning project. Feel free to fork and experiment!

## 📝 License

This project is open source and available for educational purposes.

## 👤 Author

**Bhagyesh Suthar**
- GitHub: [@Bhagyesh-08](https://github.com/Bhagyesh-08)

---

**Note**: This is a demonstration project showcasing backend engineering skills. Not intended for production use without proper security hardening, compliance review, and operational setup.
