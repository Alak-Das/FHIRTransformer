# API Reference

## Table of Contents
- [Authentication](#authentication)
- [Conversion Endpoints](#conversion-endpoints)
- [Tenant Management](#tenant-management)
- [Error Handling](#error-handling)
- [Rate Limiting & Quotas](#rate-limiting--quotas)
- [Interactive Documentation (Swagger UI)](#interactive-documentation-swagger-ui)


## Authentication

All API endpoints require **HTTP Basic Authentication**.

### Headers
```http
Authorization: Basic <base64(username:password)>
Content-Type: text/plain  (for HL7 messages)
Content-Type: application/json  (for FHIR bundles, batch requests)
```

### Rate Limiting Response Headers

All authenticated API requests include rate limit information in response headers:
- `X-RateLimit-Limit`: Maximum requests per minute for the tenant
- `X-RateLimit-Remaining`: Requests remaining in current minute
- `Retry-After`: (when 429) Seconds until rate limit resets

### Default Admin Credentials
```
Username: admin
Password: password
```

> ⚠️ **Security Warning**: Change default credentials in production via environment variables `ADMIN_USERNAME` and `ADMIN_PASSWORD`.

### Roles
- **ADMIN**: Full access (tenant management + conversions)
- **TENANT**: Conversion endpoints only

---

## Conversion Endpoints

### 1. HL7 to FHIR Conversion (Async)

**Endpoint**: `POST /api/convert/v2-to-fhir`

**Description**: Asynchronous conversion. Message is queued in RabbitMQ and processed in the background.

**Headers**:
- `Authorization`: Basic authentication (required)
- `Content-Type`: text/plain (required)
- `Idempotency-Key`: Client-generated unique key for duplicate prevention (optional, max 255 chars)

**Request**:
```http
POST /api/convert/v2-to-fhir HTTP/1.1
Host: localhost:8090
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
Content-Type: text/plain
Idempotency-Key: unique-request-id-12345

MSH|^~\&|SENDING|FACILITY|RECEIVING|FACILITY|20240119120000||ADT^A01|MSG001|P|2.5
PID|1||12345||Doe^John||19800101|M|||123 Main St^^New York^NY^10001
PV1|1|I|4N^401^01|E|||1234^Smith^John|||SVC||||||||5678|||||||||||||||||||||||20240119120000
```

**Success Response**:
```http
HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "status": "Conversion initiated",
  "transactionId": "MSG001"
}
```

**Duplicate Request Response** (same Idempotency-Key):
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "Already processed",
  "transactionId": "MSG001",
  "originalStatus": "PROCESSED"
}
```

**Error Responses**:
```json
// 400 Bad Request - Malformed HL7
{
  "error": "Bad Request",
  "message": "ca.uhn.hl7v2.HL7Exception: Invalid message structure",
  "timestamp": "2024-01-19T12:00:00"
}

// 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Invalid credentials"
}
```

**Notes**:
- Returns immediately with `202 Accepted`
- Actual conversion happens asynchronously
- **Retry Logic**: Failed messages automatically retry 3 times (5s → 15s → 45s delays) before routing to DLQ
- **Idempotency**: Duplicate requests with same `Idempotency-Key` return cached result
- Check transaction status via `/api/tenants/{tenantId}/transactions`
- Final status after all retries exhausted: Routes to Dead Letter Queue (DLQ)

---

### 2. HL7 to FHIR Conversion (Sync)

**Endpoint**: `POST /api/convert/v2-to-fhir-sync`

**Description**: Synchronous conversion. Blocks until conversion completes.

**Request**:
```http
POST /api/convert/v2-to-fhir-sync HTTP/1.1
Host: localhost:8090
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
Content-Type: text/plain

MSH|^~\&|SENDING|FACILITY|RECEIVING|FACILITY|20240119120000||ADT^A01|MSG001|P|2.5
PID|1||12345||Doe^John||19800101|M|||123 Main St^^New York^NY^10001
```

**Success Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "resourceType": "Bundle",
  "id": "MSG001",
  "type": "transaction",
  "entry": [
    {
      "fullUrl": "urn:uuid:a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "resource": {
        "resourceType": "Patient",
        "id": "12345",
        "identifier": [
          {
            "value": "12345"
          }
        ],
        "name": [
          {
            "family": "Doe",
            "given": ["John"]
          }
        ],
        "gender": "male",
        "birthDate": "1980-01-01",
        "address": [
          {
            "line": ["123 Main St"],
            "city": "New York",
            "state": "NY",
            "postalCode": "10001"
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:encounter-id",
      "resource": {
        "resourceType": "Encounter",
        ...
      }
    }
  ]
}
```

**Performance**: Typically 50-200ms for standard ADT messages

---

### 3. FHIR to HL7 Conversion (Async)

**Endpoint**: `POST /api/convert/fhir-to-v2`

**Request**:
```http
POST /api/convert/fhir-to-v2 HTTP/1.1
Host: localhost:8090
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
Content-Type: application/json

{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "id": "12345",
        "name": [{"family": "Doe", "given": ["John"]}],
        "gender": "male",
        "birthDate": "1980-01-01"
      }
    }
  ]
}
```

**Response**: `202 Accepted` (same as HL7→FHIR async)

---

### 4. FHIR to HL7 Conversion (Sync)

**Endpoint**: `POST /api/convert/fhir-to-v2-sync`

**Success Response**:
```http
HTTP/1.1 200 OK
Content-Type: text/plain

MSH|^~\&|HL7FHIRTransformer||LegacyApp||20240119120000||ADT^A01^ADT_A01|MSG001|P|2.5
PID|1||12345|||Doe^John||19800101|M
```

---

### 5. Batch HL7 to FHIR Conversion

**Endpoint**: `POST /api/convert/v2-to-fhir-batch`

**Description**: Convert multiple HL7 messages in parallel. Max recommended: 100 messages.

**Request**:
```http
POST /api/convert/v2-to-fhir-batch HTTP/1.1
Content-Type: application/json
Authorization: Basic YWRtaW46cGFzc3dvcmQ=

{
  "messages": [
    "MSH|^~\\&|...|ADT^A01|MSG001|...\rPID|...",
    "MSH|^~\\&|...|ADT^A01|MSG002|...\rPID|...",
    "MSH|^~\\&|...|ADT^A01|MSG003|...\rPID|..."
  ]
}
```

**Success Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "totalMessages": 3,
  "successCount": 2,
  "failureCount": 1,
  "results": [
    {
      "success": true,
      "output": "{\"resourceType\":\"Bundle\",...}",
      "processingTime": 45
    },
    {
      "success": true,
      "output": "{\"resourceType\":\"Bundle\",...}",
      "processingTime": 52
    },
    {
      "success": false,
      "errorMessage": "ca.uhn.hl7v2.HL7Exception: Invalid PID segment",
      "processingTime": 12
    }
  ],
  "totalProcessingTime": 109
}
```

**Performance**: Parallel processing with `parallelStream()`. Typical throughput: 10-20 messages/second.

---

### 6. Batch FHIR to HL7 Conversion

**Endpoint**: `POST /api/convert/fhir-to-v2-batch`

**Request**:
```http
POST /api/convert/fhir-to-v2-batch HTTP/1.1
Content-Type: application/json

[
  "{\"resourceType\":\"Bundle\",\"entry\":[...]}",
  "{\"resourceType\":\"Bundle\",\"entry\":[...]}",
  "{\"resourceType\":\"Bundle\",\"entry\":[...]}"
]
```

**Response**: Same structure as HL7→FHIR batch response.

---

## Tenant Management

> **Authorization**: All tenant endpoints require `ROLE_ADMIN`

### 1. List All Tenants

**Endpoint**: `GET /api/tenants`

**Request**:
```http
GET /api/tenants HTTP/1.1
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
```

**Success Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": "507f1f77bcf86cd799439011",
    "tenantId": "tenant1",
    "name": "General Hospital",
    "createdAt": "2024-01-15T10:30:00"
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "tenantId": "tenant2",
    "name": "Citywide Clinic",
    "createdAt": "2024-01-16T14:20:00"
  }
]
```

---

### 2. Onboard New Tenant

**Endpoint**: `POST /api/tenants/onboard`

**Request**:
```http
POST /api/tenants/onboard HTTP/1.1
Content-Type: application/json
Authorization: Basic YWRtaW46cGFzc3dvcmQ=

{
  "tenantId": "hospital_a",
  "password": "SecureP@ssw0rd",
  "name": "Hospital A",
  "requestLimitPerMinute": 120
}
```

**Request Fields**:
- `tenantId` (required): Unique identifier for the tenant
- `password` (required): Password for authentication
- `name` (optional): Display name for the tenant
- `requestLimitPerMinute` (optional): Rate limit (default: 60)

**Success Response**:
```http
HTTP/1.1 200 OK

{
  "id": "507f1f77bcf86cd799439013",
  "tenantId": "hospital_a",
  "name": "Hospital A",
  "requestLimitPerMinute": 120,
  "createdAt": "2024-01-19T12:00:00"
}
```

**Validation Errors**:
```json
// 400 Bad Request - Missing required fields
{
  "error": "Validation Error",
  "details": [
    "tenantId: must not be blank",
    "password: must not be blank"
  ]
}
```

---

### 3. Update Tenant

**Endpoint**: `PUT /api/tenants/{tenantId}`

**Request**:
```http
PUT /api/tenants/hospital_a HTTP/1.1
Content-Type: application/json

{
  "name": "Hospital A - Updated Name",
  "password": "NewP@ssw0rd"
}
```

**Note**: Only `name` and `password` are mutable. `tenantId` cannot be changed.

---

### 4. Delete Tenant

**Endpoint**: `DELETE /api/tenants/{tenantId}`

**Request**:
```http
DELETE /api/tenants/hospital_a HTTP/1.1
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
```

**Success Response**:
```http
HTTP/1.1 200 OK

"Tenant deleted successfully"
```

**Error Responses**:
```json
// 404 Not Found
{
  "error": "Not Found",
  "message": "Tenant with ID 'hospital_a' not found"
}
```

---

### 5. Get Tenant Transactions (Audit Log)

**Endpoint**: `GET /api/tenants/{tenantId}/transactions`

**Query Parameters**:
- `startDate` (required): ISO 8601 datetime (e.g., `2024-01-01T00:00:00`)
- `endDate` (required): ISO 8601 datetime
- `page` (optional, default=0): Page number
- `size` (optional, default=20): Page size

**Request**:
```http
GET /api/tenants/hospital_a/transactions?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59&page=0&size=10 HTTP/1.1
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
```

**Success Response**:
```http
HTTP/1.1 200 OK

{
  "totalCount": 150,
  "totalPages": 15,
  "currentPage": 0,
  "statusCounts": {
    "ACCEPTED": 10,
    "PROCESSED": 135,
    "FAILED": 5
  },
  "transactions": [
    {
      "HL7FHIRTransformerId": "txn-mongo-id-123",
      "originalMessageId": "MSG001",
      "messageType": "V2_TO_FHIR",
      "status": "PROCESSED",
      "timestamp": "2024-01-19T12:00:00"
    },
    {
      "originalMessageId": "MSG002",
      "messageType": "FHIR_TO_V2",
      "status": "FAILED",
      "timestamp": "2024-01-19T12:05:00"
    },
    ...
  ]
}
```

---

## Error Handling

### Global Error Format
```json
{
  "error": "<HTTP Status Reason>",
  "message": "<Detailed error description>",
  "timestamp": "2024-01-19T12:00:00"
}
```

### Common HTTP Status Codes

| Code | Meaning | Cause |
|------|---------|-------|
| 200 | OK | Synchronous conversion succeeded |
| 202 | Accepted | Async conversion queued |
| 400 | Bad Request | Invalid HL7/FHIR syntax, validation failure |
| 401 | Unauthorized | Missing/invalid credentials |
| 403 | Forbidden | Insufficient permissions (e.g., TENANT trying to access admin endpoint) |
| 404 | Not Found | Tenant not found |
| 500 | Internal Server Error | Unexpected exception |

### FHIR Validation Errors

When `FhirValidationService` detects issues:
```json
{
  "error": "Bad Request",
  "message": "FHIR validation failed: Patient.birthDate - Invalid date format",
  "timestamp": "2024-01-19T12:00:00"
}
```

### Dead Letter Queue (DLQ)

Failed async messages are routed to:
- **HL7→FHIR**: `hl7-messages-dlq` (exchange: `hl7-messages-dlx`)
- **FHIR→HL7**: `fhir-to-v2-dlq` (exchange: `fhir-to-v2-dlx`)

**Access DLQ via RabbitMQ Management UI**:
- URL: `http://localhost:15672`
- Credentials: `guest/guest`
- Navigate to Queues → `hl7-messages-dlq`

---

## Rate Limiting & Quotas

### Current Limits
- **Batch Size**: Max 100 messages per batch request
- **RabbitMQ Prefetch**: 50 messages per consumer
- **Thread Pool**: 20 max concurrent async tasks

### Recommended Load
- **Sync Endpoints**: ~50 requests/second per instance
- **Async Endpoints**: ~100 messages/second (limited by RabbitMQ throughput)
- **Batch Endpoints**: 10-20 batches/second

### Scaling Recommendations
For higher throughput:
1. Increase RabbitMQ concurrency in `application.properties`
2. Scale horizontally (multiple app instances + load balancer)
3. Enable MongoDB replica set for read scaling
4. Use Redis cluster for distributed caching

---

## Examples

### cURL Examples

```bash
# Sync HL7 to FHIR
curl -X POST http://localhost:8090/api/convert/v2-to-fhir-sync \
  -H "Content-Type: text/plain" \
  -u admin:password \
  --data-binary @sample_hl7.txt

# Async FHIR to HL7
curl -X POST http://localhost:8090/api/convert/fhir-to-v2 \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d @sample_bundle.json

# Batch Conversion
curl -X POST http://localhost:8090/api/convert/v2-to-fhir-batch \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{"messages":["MSH|...","MSH|..."]}'

# Onboard Tenant
curl -X POST http://localhost:8090/api/tenants/onboard \
  -H "Content-Type: application/json" \
  -u admin:password \
  -d '{"tenantId":"clinic1","password":"secret","name":"Clinic 1"}'

# Get Audit Logs
curl -X GET "http://localhost:8090/api/tenants/admin/transactions?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59" \
  -u admin:password
```

### Postman Collection

A comprehensive Postman collecti[Download Postman Collection](./postman/hl7-fhir-transformer.postman_collection.json)

### Running with Newman
```bash
newman run postman/hl7-fhir-transformer.postman_collection.json \
  -e postman/hl7-fhir-transformer.local.postman_environment.json
```

---

## Interactive Documentation (Swagger UI)

For an interactive experience where you can test the APIs in real-time, visit the built-in Swagger UI:

- **Local URL**: `http://localhost:8080/swagger-ui.html`
- **Docker URL**: `http://localhost:8090/swagger-ui.html`

The Swagger UI provides:
- Live API testing with Basic Auth support.
- Detailed request/response schemas.
- Real-time specification in OpenAPI 3.0 format.

## Next Steps

- **For architecture details**, see [Architecture & Design Patterns](architecture.md)
- **For deployment**, see [Setup & Deployment](setup-deployment.md)
- **For configuration**, see [Configuration Guide](configuration.md)

