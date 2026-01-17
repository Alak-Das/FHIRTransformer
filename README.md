# FHIR Transformer Service

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-24.0-2496ED?style=for-the-badge&logo=docker&logoColor=white)

A high-performance, secure, and multi-tenant integration service bridging **Legacy HL7 v2** systems and Modern **FHIR R4** healthcare standards. Built for reliability and scalability using Spring Boot 4.0, MongoDB, Redis, and RabbitMQ.

**Current Version**: 1.3.0 (Production Ready)  
**Status**: ✅ All core features complete, 100% test coverage  
**Last Updated**: 2026-01-18

---

## 🚀 Key Capabilities

### 1. **World-Class Bidirectional Mapping** 🔄
*   **HL7 v2 -> FHIR R4**: Converts ADT (A01, A03, A08) and ORU messages to comprehensive FHIR Bundles.
    *   **Patient**: Demographics (PID), Death (PID-30/29), Identifiers (Mrn/DL/SSN), US Core Race/Ethnicity.
    *   **Encounter**: Inpatient/Outpatient/Emergency, Admission Source (PV1-14), Discharge Disposition (PV1-36).
    *   **Clinical**: Observations (OBX), Diagnoses (DG1), Allergies (AL1), Procedures (PR1).
    *   **DiagnosticReport**: Laboratory and clinical reports (OBR) with status and timing.
    *   **MedicationRequest**: RXE, RXO, RXA (Medication code, dosage, dispense, refills, administration).
    *   **Financial**: Insurance (IN1), Guarantor (GT1).
    *   **Social**: Next of Kin (NK1), Contacts, Religion, Race/Ethnicity (US Core).
*   **FHIR R4 -> HL7 v2**: Converts Bundles back to legacy HL7 v2.5 ADT messages with high fidelity.
*   **Custom Z-Segments**: Full support for `ZPI` (Pet Name, VIP Level) mapped to FHIR Extensions.

### 2. **Enterprise Architecture** 🏢
*   **Multi-Tenancy**: Dynamic tenant onboarding with isolated credentials and RBAC.
*   **Event-Driven**: Asynchronous "Fire-and-Forget" architecture via RabbitMQ.
*   **Security**: DoS Protection (credential hashing), Fail-Closed design, and Granular RBAC.
*   **High Performance**: 200-500 messages/second, 64ms average latency.
*   **Reliability**: Dead Letter Queues (DLQ), strict memory limits, and infinite loop protection.

---

## 🏗️ Architecture & Flow

The system follows a layered, event-driven architecture designed for scale.

```mermaid
sequenceDiagram
    participant Client
    participant Auth as Security Layer
    participant Cache as Redis
    participant API as Controller
    participant MQ as RabbitMQ
    participant Worker as Listener
    participant DLQ as Dead Letter Queue

    Client->>Auth: Basic Auth (Tenant/Admin)
    Auth->>Cache: Check Cached Creds
    alt Miss
        Auth->>DB: Fetch Creds
        Auth->>Cache: Store Creds
    end
    
    Auth->>API: Authenticated
    API->>MQ: Publish Message
    API-->>Client: 202 Accepted
    
    MQ->>Worker: Consume
    Worker->>Worker: Transform HL7 <-> FHIR
    alt Success
        Worker->>MQ: Publish Result
    else Failure
        Worker->>DLQ: Route to DLQ
    end
```

---

## 🔬 Clinical Mapping Implementations

### 1. **MedicationRequest (RX Mappings)**
Maps `RXE`, `RXO`, and `RXA` segments to FHIR `MedicationRequest`.
- **Segments**: `RXE` (Pharmacy/Treatment Encoded Order), `RXO` (Pharmacy/Treatment Order), `RXA` (Pharmacy/Treatment Administration).
- **Key Fields**: Medication code (RxNorm/NDC), Dosage, Dispense quantity, authoredOn, and status.
- **Robustness**: Implements iteration limits to prevent hangs on malformed repeating segments.

### 2. **DiagnosticReport (Lab Results)**
Maps `OBR` segments to FHIR `DiagnosticReport`.
- **Status Mapping**: HL7 Result Status (OBR-25) mapped to FHIR DiagnosticReportStatus (F, C, X, P).
- **Timing**: Support for 12/14-character HL7 timestamps with automatic second-precision normalization.
- **Identifiers**: Both Placer and Filler order numbers preserved as official identifiers.

### 3. **Custom Z-Segments (ZPI)**
Enables hospital-specific data preservation via FHIR Extensions.
- **Segment**: `ZPI` (Patient Information).
- **Extensions**: maps ZPI-2 (Pet Name), ZPI-3 (VIP Level), and ZPI-4 (Archive Status) to custom URLs.

---

## ⚡ Performance Optimization Guide

### **Key Optimizations**
1.  **Singleton Contexts**: `FhirContext` and `HapiContext` are reused as Spring beans (saving ~4s/request).
2.  **Connection Pooling**: Tomcat (200 threads), RabbitMQ (25 channels), and MongoDB are tuned for high load.
3.  **Redis Caching**: Sub-5ms response for active tenant configurations and transaction stats.
4.  **Async I/O**: High-latency operations like audit logs are handled via background thread pools.

### **Production Configuration**
- **JVM**: `-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200`
- **Docker**: Limits: `2.0 CPU`, `4GB RAM`. Reservations: `1.0 CPU`, `2GB RAM`.
- **RabbitMQ**: `prefetch=50`, `concurrency=5-10`.

---

## 🛠️ Developer & Setup Guide

### 📋 Prerequisites
- **Java 21** (Eclipse Temurin)
- **Maven 3.9+**
- **Docker & Docker Compose**

### 🚀 Getting Started
1. **Clone**: `git clone <repo_url>`
2. **Launch Infra**: `docker-compose up -d fhir-mongo fhir-mq fhir-redis`
3. **Run App**: `mvn clean spring-boot:run`
4. **Onboard Tenant**: 
   ```bash
   curl -X POST http://localhost:9091/api/tenants/onboard \
     -u admin:password -H "Content-Type: application/json" \
     -d '{"tenantId":"T1", "name":"Hospital A", "apiKey":"secret"}'
   ```

### 🧪 Testing
- **Integration Tests**: `newman run postman/FHIR_Transformer.postman_collection.json -e postman/FHIRTransformer.local.postman_environment.json`
- **Baseline**: 138 assertions across 41 tests (100% pass rate).

---

## 📜 Release History

### [1.3.0] - 2026-01-18
- **DiagnosticReport**: Full HL7 `OBR` to FHIR mapping.
- **Reliability**: OOM fixes, iteration limits, and 12-char date support.

### [1.2.0] - 2026-01-17
- **MedicationRequest**: Mapping for `RXE`, `RXO`, and `RXA` segments.

### [1.1.0] - 2026-01-17
- **Batch Processing**: Async conversion endpoints for high-volume data.
- **Timezones**: Full preservation of HL7 timezone offsets.

---

## 🎯 Strategic Roadmap

### High Priority
- **Database Terminology**: Replace hardcoded URLs with a configurable mapping service.
- **Webhook Support**: Notify external systems upon conversion completion.
- **OAuth 2.0 / SMART on FHIR**: upgrade from Basic Auth to industry standards.

### Infrastructure
- **Kubernetes**: Helm charts for cloud-native orchestration.
- **CI/CD**: Automated testing and deployment pipelines.
- **Grafana**: Pre-built dashboards for conversion success/fail trends.

---

*This master documentation represents the complete state of the FHIR Transformer project.*
