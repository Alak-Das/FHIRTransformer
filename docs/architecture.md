# System Architecture & Design Patterns

## Table of Contents
- [High-Level Architecture](#high-level-architecture)
- [Design Patterns](#design-patterns)
- [Component Architecture](#component-architecture)
- [Data Flow](#data-flow)
- [Technology Stack](#technology-stack)

## High-Level Architecture

FHIR Transformer follows a **Layered Microservice Architecture** with **Event-Driven Asynchronous Processing** for scalability and decoupling.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Applications                       │
│         (External Systems, EHR/HIE, Legacy HL7 Systems)         │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP/REST (Basic Auth)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway Layer                           │
│  ┌───────────────────┐           ┌────────────────────┐        │
│  │ ConverterController│          │ TenantController   │        │
│  │ - Sync Endpoints  │          │ - Admin Endpoints  │        │
│  │ - Async Endpoints │          │ - RBAC Enforcement │        │
│  │ - Batch Endpoints │          └────────────────────┘        │
│  └───────────────────┘                                          │
└────────────────────────┬────────────────────────┬───────────────┘
          Sync Path      │                        │  Async Path
                         ▼                        ▼
┌──────────────────────────────────┐  ┌──────────────────────────┐
│     Business Logic Layer         │  │   Message Broker Layer   │
│  ┌─────────────────────────┐    │  │  ┌─────────────────────┐ │
│  │ Hl7ToFhirService        │    │  │  │  RabbitMQ Exchange  │ │
│  │ FhirToHl7Service        │────┼──┼─▶│  hl7-messages       │ │
│  │ BatchConversionService  │    │  │  │  fhir-messages      │ │
│  │ TenantService           │    │  │  └──────────┬──────────┘ │
│  │ AuditService            │    │  │             │             │
│  └─────────────────────────┘    │  │             ▼             │
│  ┌─────────────────────────┐    │  │  ┌─────────────────────┐ │
│  │   Converter Layer       │    │  │  │   Queues (DLQ)      │ │
│  │  - PatientConverter     │    │  │  │  - hl7-messages-q   │ │
│  │  - ObservationConverter │    │  │  │  - fhir-to-v2-q     │ │
│  │  - EncounterConverter   │    │  │  │  - output queues    │ │
│  │  - (13 more converters) │    │  │  └──────────┬──────────┘ │
│  └─────────────────────────┘    │  └─────────────┼────────────┘
│  ┌─────────────────────────┐    │                │
│  │   Validation Layer      │    │                ▼
│  │  - FhirValidationService│    │  ┌──────────────────────────┐
│  │  - MessageEnrichmentSvc │    │  │  Listener Layer          │
│  └─────────────────────────┘    │  │  ┌─────────────────────┐ │
└────────────────┬─────────────────┘  │  │ Hl7MessageListener  │ │
                 │                     │  │ FhirMessageListener │ │
                 ▼                     │  └──────────┬──────────┘ │
┌───────────────────────────────────┐ └─────────────┼────────────┘
│      Persistence & Cache Layer    │               │
│  ┌─────────────┐  ┌─────────────┐│               │
│  │   MongoDB   │  │    Redis    ││◀──────────────┘
│  │  - Tenants  │  │  - Caching  ││  (Tenant Context,
│  │  - TxnLogs  │  │  - Sessions ││   Audit Updates)
│  └─────────────┘  └─────────────┘│
└───────────────────────────────────┘
```

### Architecture Layers

#### 1. **API Gateway Layer** (Controllers)
- **Entry point** for all client requests
- **Responsibilities**:
  - HTTP request handling and routing
  - Authentication & Authorization (Spring Security + Basic Auth)
  - Request validation
  - Tenant context extraction
  - Response formatting

#### 2. **Business Logic Layer** (Services)
- **Core transformation logic**
- **Responsibilities**:
  - Message conversion orchestration
  - Business rule enforcement
  - Transaction management
  - Audit logging

#### 3. **Converter Layer** (Specialized Converters)
- **Segment-to-Resource mapping**
- **Responsibilities**:
  - Granular HL7 segment parsing
  - FHIR resource construction
  - Extension handling (Z-segments)
  - Timezone preservation

#### 4. **Message Broker Layer** (RabbitMQ)
- **Asynchronous decoupling**
- **Responsibilities**:
  - Queue management
  - Message routing
  - Dead Letter Queue (DLQ) handling
  - Retry logic

#### 5. **Listener Layer** (RabbitMQ Consumers)
- **Asynchronous message processing**
- **Responsibilities**:
  - Queue consumption
  - Tenant context propagation (via headers)
  - Transaction status updates
  - Error handling with DLQ failover

#### 6. **Persistence & Cache Layer**
- **Data storage and performance**
- **MongoDB**: Tenant data, transaction logs
- **Redis**: Distributed caching for frequent queries

---

## Design Patterns

### 1. **Strategy Pattern** (Converter Architecture)
Each FHIR resource type has a dedicated converter implementing a common interface.

```java
// All converters follow this pattern
@Component
public class PatientConverter implements SegmentConverter {
    public Reference convert(Terser terser, Bundle bundle) {
        // Extract HL7 PID/PD1 segments
        // Map to FHIR Patient resource
        // Add to bundle
        // Return reference
    }
}
```

**Benefits**:
- **Extensibility**: New resource types = new converter classes
- **Maintainability**: Each converter is self-contained
- **Testability**: Easy to unit test individual converters

### 2. **Facade Pattern** (Service Layer)
`Hl7ToFhirService` and `FhirToHl7Service` act as facades, orchestrating multiple converters.

```java
public String convertHl7ToFhir(String hl7Message) {
    // 1. Parse HL7
    Message hapiMsg = hl7Context.getPipeParser().parse(hl7Message);
    
    // 2. Create FHIR Bundle
    Bundle bundle = new Bundle();
    
    // 3. Orchestrate converters
    patientConverter.convert(terser, bundle);
    encounterConverter.convert(terser, bundle);
    observationConverter.convert(terser, bundle);
    // ... more converters
    
    // 4. Validate and return
    return fhirContext.newJsonParser().encodeResourceToString(bundle);
}
```

### 3. **Repository Pattern** (Data Access)
```java
@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {
    Optional<Tenant> findByTenantId(String tenantId);
}
```

### 4. **Observer Pattern** (Event-Driven Architecture)
RabbitMQ listeners act as observers responding to queue events.

```java
@RabbitListener(queues = "${app.rabbitmq.queue}")
public void receiveMessage(String hl7Message, 
                          @Header(value = "tenantId") String tenantId) {
    // React to message arrival
}
```

### 5. **Template Method Pattern** (Batch Processing)
```java
public BatchConversionResponse convertInParallel(
    List<String> messages, 
    Function<String, String> converter
) {
    // Template defines the structure
    return messages.parallelStream()
        .map(msg -> {
            try {
                String result = converter.apply(msg);  // Subclass-specific
                return ConversionResult.success(result);
            } catch (Exception e) {
                return ConversionResult.failure(e);
            }
        })
        .collect(Collectors.toList());
}
```

### 6. **Dependency Injection** (Spring IoC)
All components use constructor injection for testability and loose coupling.

```java
@Service
public class Hl7ToFhirService {
    private final PatientConverter patientConverter;
    private final EncounterConverter encounterConverter;
    
    @Autowired
    public Hl7ToFhirService(
        PatientConverter patientConverter,
        EncounterConverter encounterConverter
    ) {
        this.patientConverter = patientConverter;
        this.encounterConverter = encounterConverter;
    }
}
```

### 7. **Cache-Aside Pattern** (Redis Caching)
```java
@Cacheable(value = "tenant", key = "#tenantId")
public Optional<Tenant> findByTenantId(String tenantId) {
    return tenantRepository.findByTenantId(tenantId);
}
```

---

## Component Architecture

### Core Components

#### **ConverterController**
- **Type**: REST Controller
- **Endpoints**: 
  - `POST /api/convert/v2-to-fhir` (Async)
  - `POST /api/convert/v2-to-fhir-sync` (Sync)
  - `POST /api/convert/fhir-to-v2` (Async)
  - `POST /api/convert/fhir-to-v2-sync` (Sync)
  - `POST /api/convert/v2-to-fhir-batch` (Batch)
  - `POST /api/convert/fhir-to-v2-batch` (Batch)
- **Responsibilities**: 
  - Request routing
  - Tenant extraction
  - Message enrichment (transaction ID)
  - Async: RabbitMQ dispatch
  - Sync: Direct service invocation

#### **TenantController**
- **Type**: REST Controller (Admin only)
- **Endpoints**:
  - `GET /api/tenants` - List all tenants
  - `POST /api/tenants/onboard` - Create tenant
  - `PUT /api/tenants/{id}` - Update tenant
  - `DELETE /api/tenants/{id}` - Delete tenant
  - `GET /api/tenants/{id}/transactions` - Get audit logs
- **Security**: `@PreAuthorize("hasRole('ADMIN')")`

#### **Hl7ToFhirService**
- **Type**: Business Service
- **Converters Orchestrated** (15 total):
  1. PatientConverter (PID, PD1)
  2. EncounterConverter (PV1, PV2)
  3. ObservationConverter (OBX)
  4. AllergyConverter (AL1)
  5. ConditionConverter (DG1)
  6. ProcedureConverter (PR1)
  7. MedicationConverter (RXE, RXR)
  8. MedicationAdministrationConverter (RXA)
  9. InsuranceConverter (IN1, GT1)
  10. ImmunizationConverter (RXA)
  11. AppointmentConverter (SCH)
  12. ServiceRequestConverter (OBR)
  13. DiagnosticReportConverter (ORU^R01)
  14. PractitionerConverter
  15. SegmentConverter (Z-segments)

#### **FhirToHl7Service**
- **Type**: Business Service
- **Responsibilities**:
  - Parse FHIR Bundle (JSON)
  - Extract resources
  - Map to HL7 ADT^A01 structure
  - Populate MSH, PID, PV1, PV2, OBX, AL1, DG1, PR1, IN1, GT1, etc.

#### **Hl7MessageListener** & **FhirMessageListener**
- **Type**: RabbitMQ Consumers
- **Key Features**:
  - Tenant context propagation via message headers
  - Transaction status updates (PROCESSED / FAILED)
  - Error handling with DLQ routing
  - Context cleanup (`TenantContext.clear()` in `finally` block)

#### **AuditService**
- **Type**: Async Service
- **Responsibilities**:
  - Log conversion transactions to MongoDB
  - Update status (ACCEPTED → PROCESSED / FAILED)
  - Async annotation for non-blocking writes

---

## Data Flow

### Synchronous Conversion Flow (HL7 → FHIR)

```
[Client] 
    │
    │ POST /api/convert/v2-to-fhir-sync
    │ Authorization: Basic YWRtaW46cGFzc3dvcmQ=
    │ Content-Type: text/plain
    │ Body: <HL7 Message>
    ▼
[ConverterController.convertToFhirSync]
    │
    ├─▶ Extract tenantId from Principal
    ├─▶ Set TenantContext.setTenantId(tenantId)
    ├─▶ Call messageEnrichmentService.ensureHl7TransactionId(hl7Message)
    │
    ▼
[Hl7ToFhirService.convertHl7ToFhir]
    │
    ├─▶ Parse HL7 using HAPI Parser
    ├─▶ Create Terser for segment navigation
    ├─▶ Create FHIR Bundle (type: TRANSACTION)
    │
    ├─▶ PatientConverter.convert() → Patient resource
    ├─▶ EncounterConverter.convert() → Encounter resource
    ├─▶ ObservationConverter.convert() → Observation resources
    ├─▶ ... (loop through all converters)
    │
    ├─▶ FhirValidationService.validateBundle(bundle)
    ├─▶ Serialize to JSON
    │
    ▼
[AuditService.logTransaction (Async)]
    │
    └─▶ Save to MongoDB: TransactionRecord{
            tenantId, transactionId, messageType: "V2_TO_FHIR",
            status: "COMPLETED", timestamp
        }
    ▼
[Response: 200 OK]
    Body: {FHIR Bundle JSON}
```

### Asynchronous Conversion Flow (HL7 → FHIR)

```
[Client]
    │
    │ POST /api/convert/v2-to-fhir
    │ Authorization: Basic YWRtaW46cGFzc3dvcmQ=
    │ Body: <HL7 Message>
    ▼
[ConverterController.convertToFhir]
    │
    ├─▶ Extract tenantId
    ├─▶ Enrich message with transaction ID
    ├─▶ AuditService.logTransaction(status: "ACCEPTED")
    │
    ├─▶ rabbitTemplate.convertAndSend(
    │       exchange: "hl7-messages-exchange",
    │       routingKey: "hl7.message.routing",
    │       message: hl7Message,
    │       headers: { tenantId: "admin" }
    │   )
    │
    ▼
[Response: 202 Accepted]
    {
      "status": "Conversion initiated",
      "transactionId": "MSG001"
    }

---[Async Processing in Background]---

[RabbitMQ Queue: hl7-messages-queue]
    │
    ▼
[Hl7MessageListener.receiveMessage]
    │
    ├─▶ Extract tenantId from @Header
    ├─▶ TenantContext.setTenantId(tenantId)
    │
    ├─▶ try {
    │       Hl7ToFhirService.convertHl7ToFhir(hl7Message)
    │       RabbitTemplate.send(outputQueue: "fhir-messages-queue", fhirBundle)
    │       AuditService.updateTransactionStatus(txnId, "PROCESSED")
    │   }
    │   catch (Exception e) {
    │       Extract transactionId from MSH-10
    │       AuditService.updateTransactionStatus(txnId, "FAILED")
    │       throw new RuntimeException() → Routes to DLQ
    │   }
    │   finally {
    │       TenantContext.clear()
    │   }
    ▼
[Output Queue: fhir-messages-queue]
    (Consumed by downstream systems)
```

### Batch Conversion Flow

```
[Client]
    │
    │ POST /api/convert/v2-to-fhir-batch
    │ Body: {
    │   "messages": ["<HL7-1>", "<HL7-2>", ..., "<HL7-N>"]
    │ }
    ▼
[ConverterController.convertHl7ToFhirBatch]
    │
    ▼
[BatchConversionService.convertHl7ToFhirBatch]
    │
    ├─▶ Timer.start() for metrics
    │
    ├─▶ messages.parallelStream()
    │       .map(hl7 -> {
    │           try {
    │               String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
    │               return ConversionResult.success(fhir);
    │           } catch (Exception e) {
    │               return ConversionResult.failure(e.getMessage());
    │           }
    │       })
    │       .collect(Collectors.toList())
    │
    ├─▶ Timer.stop() and record duration
    │
    ▼
[Response: 200 OK]
    {
      "totalMessages": 10,
      "successCount": 9,
      "failureCount": 1,
      "results": [
          { "success": true, "output": "<FHIR Bundle>", "processingTime": 45 },
          { "success": false, "errorMessage": "Invalid segment", "processingTime": 12 },
          ...
      ],
      "totalProcessingTime": 523
    }
```

---

## Technology Stack

### Core Framework
- **Spring Boot**: 4.0.1
- **Java**: 21 (LTS)
- **Build Tool**: Maven 3.9.9

### HL7 & FHIR Libraries
- **HAPI FHIR**: 7.6.1 (R4 structures, validation)
- **HAPI HL7 v2**: 2.5.1 (v2.3, v2.4, v2.5 support)

### Messaging & Integration
- **RabbitMQ**: 3.x (Alpine image with management plugin)
- **Spring AMQP**: Integrated via `spring-boot-starter-amqp`

### Data Persistence
- **MongoDB**: Latest (document storage for tenants, audit logs)
- **Spring Data MongoDB**: Repository pattern

### Caching & Performance
- **Redis**: 7.x (Alpine image)
- **Spring Data Redis**: Lettuce client
- **Spring Cache Abstraction**: Cache-aside pattern

### Security
- **Spring Security**: Basic Authentication
- **BCryptPasswordEncoder**: Password hashing
- **RBAC**: Role-based access control (ADMIN, TENANT)

### Observability
- **Spring Actuator**: Health checks, metrics
- **Micrometer**: Metrics collection
- **Prometheus**: Metrics export (compatible)
- **SLF4J + Logback**: Logging

### Testing
- **JUnit 5**: Unit testing framework
- **Mockito**: Mocking framework
- **Spring Boot Test**: Integration testing
- **Postman/Newman**: API integration tests

### Deployment
- **Docker**: Multi-stage builds (Maven + JRE)
- **Docker Compose**: Orchestration (4 services)
- **Base Images**: 
  - `maven:3.9.9-eclipse-temurin-21-alpine` (build)
  - `eclipse-temurin:21-jre-alpine` (runtime)

---

## Scalability Considerations

### Horizontal Scaling
- **Stateless Controllers**: Can be replicated behind a load balancer
- **RabbitMQ Clustering**: Multiple broker nodes for high availability
- **Redis Cluster**: Distributed cache with sharding
- **MongoDB Replica Set**: Read scaling and failover

### Performance Optimizations
- **Connection Pooling**: 
  - RabbitMQ: 25 cached channels
  - MongoDB: Default connection pool
  - Redis: Lettuce pool (max 8 active)
- **Async Processing**: Non-blocking audit logs, message listeners
- **Batch Processing**: Parallel stream processing with `parallelStream()`
- **Caching Strategy**: Cache-aside with configurable TTL (1 hour default)
- **HTTP/2 & Compression**: Enabled for reduced payload size

### Concurrency Configuration
```properties
# RabbitMQ Listeners
spring.rabbitmq.listener.simple.concurrency=5
spring.rabbitmq.listener.simple.max-concurrency=10
spring.rabbitmq.listener.simple.prefetch=50

# Async Thread Pool
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20
spring.task.execution.pool.queue-capacity=100

# Tomcat
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=10
server.tomcat.max-connections=10000
```

---

## Next Steps

- **For API details**, see [API Reference](api-reference.md)
- **For deployment**, see [Setup & Deployment](setup-deployment.md)
- **For configuration**, see [Configuration Guide](configuration.md)

