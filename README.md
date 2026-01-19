# FHIR<->HL7-Transformer Documentation

## Overview

FHIR<->HL7-Transformer is an enterprise-grade, high-performance bidirectional message conversion engine that transforms HL7 v2.x messages into FHIR R4 resources and vice versa. Built on Spring Boot 4.0.1 with Java 21, it employs asynchronous message processing via RabbitMQ, multi-tenant architecture, role-based access control, and distributed caching for optimal performance.

## Quick Links

- [Architecture & Design Patterns](docs/architecture.md)
- [API Reference](docs/api-reference.md)
- [Setup & Deployment](docs/setup-deployment.md)
- [Configuration Guide](docs/configuration.md)


## Key Features

### Core Capabilities
- **Bidirectional Conversion**: HL7 v2.x ↔ FHIR R4 with full resource mapping
- **Async & Sync Processing**: Both synchronous REST APIs and asynchronous RabbitMQ-based processing
- **Batch Operations**: Parallel batch conversion with configurable concurrency
- **Multi-Tenancy**: Complete tenant isolation with per-tenant user management
- **Custom Z-Segment Support**: Extensible mapping for non-standard HL7 segments via FHIR extensions

### Enterprise Features
- **Role-Based Access Control**: Granular permissions (ADMIN, TENANT roles)
- **Distributed Caching**: Redis-based caching with configurable TTL
- **Transaction Auditing**: Comprehensive audit logs with status tracking (ACCEPTED, PROCESSED, FAILED)
- **Idempotency Support**: RFC 7231-compliant duplicate request prevention via `Idempotency-Key` header
- **Automatic Retry Logic**: 3-tier exponential backoff (5s → 15s → 45s) for transient failures
- **Per-Tenant Rate Limiting**: Configurable requests-per-minute limits with Redis-based tracking
- **Dead Letter Queue**: Automatic DLQ handling for failed messages after retries
- **Metrics & Monitoring**: Prometheus-compatible metrics via Spring Actuator

### Technical Stack
- **Framework**: Spring Boot 4.0.1, Java 21
- **Message Broker**: RabbitMQ 3.x with management console
- **Database**: MongoDB (document storage)
- **Cache**: Redis 7.x (distributed caching)
- **HL7/FHIR Libraries**: HAPI FHIR 7.6.1, HAPI HL7 v2 2.5.1
- **Deployment**: Docker Compose, Multi-stage Dockerfile

## Supported HL7 Versions
- HL7 v2.3
- HL7 v2.4
- HL7 v2.5 (Primary)

## Supported FHIR Resources

### Administrative Resources
- Patient (PID, PD1)
- Practitioner (Custom mappings)
- RelatedPerson (NK1)

### Clinical Resources
- Encounter (PV1, PV2)
- Observation (OBX)
- AllergyIntolerance (AL1)
- Condition (DG1)
- Procedure (PR1)
- DiagnosticReport (ORU^R01)
- ServiceRequest (OBR)
- Immunization (RXA)
- Appointment (SCH)

### Medication Resources
- MedicationRequest (RXE, RXR)
- MedicationAdministration (RXA)

### Financial Resources
- Coverage (IN1, insurance)
- Account (GT1, guarantor)

## Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.9+
- Postman (for testing)

### Quick Start

```bash
# Clone repository
git clone <repository-url>
cd fhirhl7transformer

# Start infrastructure services
docker-compose up -d

# Application runs on http://localhost:8090
# RabbitMQ Management UI: http://localhost:15672 (guest/guest)
# MongoDB: mongodb://localhost:27017/fhirhl7transformer
# Redis: redis://localhost:6379
```

### First API Call

```bash
# Convert HL7 to FHIR (Sync)
curl -X POST http://localhost:8090/api/convert/v2-to-fhir-sync \
  -H "Content-Type: text/plain" \
  -u admin:password \
  --data "MSH|^~\&|SENDING|FACILITY|RECEIVING|FACILITY|20240119120000||ADT^A01|MSG001|P|2.5
PID|1||12345||Doe^John||19800101|M|||123 Main St^^New York^NY^10001"

# Convert HL7 to FHIR (Async with Idempotency)
curl -X POST http://localhost:8090/api/convert/v2-to-fhir \
  -H "Content-Type: text/plain" \
  -H "Idempotency-Key: unique-request-123" \
  -u admin:password \
  --data "MSH|^~\&|SENDING|FACILITY|RECEIVING|FACILITY|20240119120000||ADT^A01|MSG001|P|2.5
PID|1||12345||Doe^John||19800101|M|||123 Main St^^New York^NY^10001"
```

## Project Structure

```
fhirhl7transformer/
├── src/main/java/com/fhirhl7transformer/
│   ├── config/              # Configuration classes (Security, RabbitMQ, Cache, etc.)
│   ├── controller/          # REST controllers (Converter, Tenant)
│   ├── dto/                 # Data Transfer Objects
│   ├── exception/           # Custom exceptions
│   ├── listener/            # RabbitMQ message listeners
│   ├── model/               # Domain models (Tenant, TransactionRecord, enums)
│   ├── repository/          # MongoDB repositories
│   ├── service/             # Business logic services
│   │   └── converter/       # Segment-specific converters
│   └── util/                # Utility classes
├── src/main/resources/
│   └── application.properties
├── src/test/java/           # Unit and integration tests
├── postman/                 # Postman collection for integration tests
├── docker-compose.yml       # Multi-container orchestration
├── Dockerfile               # Multi-stage build
├── pom.xml                  # Maven dependencies
└── docs/                    # Comprehensive documentation
```

## Documentation Index

### For Developers
1. **[Architecture & Design Patterns](docs/architecture.md)** - System architecture, design patterns, and component interactions
2. **[API Reference](docs/api-reference.md)** - Complete REST API documentation with examples

### For DevOps
1. **[Setup & Deployment](docs/setup-deployment.md)** - Installation, deployment, and scaling guide
2. **[Configuration Guide](docs/configuration.md)** - Complete configuration reference


## License

[Add your license information here]

## Support

For issues, questions, or contributions, please refer to the project's issue tracker.
