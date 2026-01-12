# HL7 v2 ↔ FHIR Migration Tool

A high-performance, bi-directional converter bridging HL7 v2 legacy messaging and HL7 FHIR R4 standard. Built with Java 21, Spring Boot, and HAPI FHIR/HL7 libraries.

## Features
- **HL7 v2 to FHIR R4**: Converts ADT messages (and others) to FHIR Bundles.
- **FHIR R4 to HL7 v2**: Converts FHIR Bundles to HL7 v2.5 ADT^A01 messages.
- **Async Processing**: Uses RabbitMQ for high-throughput, event-driven processing.
- **Dockerized**: specific `Dockerfile` and `docker-compose.yml` for easy deployment.

## Architecture
The application uses an event-driven architecture for the `v2-to-fhir` flow:
1.  **Ingestion**: `POST /api/convert/v2-to-fhir` receives a message and immediately returns `202 Accepted`.
2.  **Queue**: The message is published to `hl7-messages-queue`.
3.  **Validation & Conversion**: A background listener (`Hl7MessageListener`) processes the message.
4.  **Output**: The resulting FHIR Bundle is published to `fhir-messages-queue`.

## Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose (optional but recommended)

## Quick Start (Docker)
The easiest way to run the application and RabbitMQ:

```bash
docker-compose up -d
```
The API is available at `http://localhost:8080`. RabbitMQ Management UI is at `http://localhost:15672`.

## Manual Build & Run
If you don't use Docker, you must have a local RabbitMQ instance running on default ports.

```bash
mvn clean package
java -jar target/fhir-transformer-0.0.1-SNAPSHOT.jar
```

## API Usage

### 1. Async HL7 v2 to FHIR (Recommended)
**Endpoint**: `POST /api/convert/v2-to-fhir`
**Content-Type**: `text/plain`
**Body**: Pipe-delimited HL7 message.
**Response**: `202 Accepted`

**Example Request**:
```
MSH|^~\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01||PID|1||100||DOE^JOHN||19700101|M||||||||||1000
```
*Note: Connect to RabbitMQ `fhir-messages-queue` to receive the result.*

### 2. Synchronous HL7 v2 to FHIR (Legacy/Debug)
**Endpoint**: `POST /api/convert/v2-to-fhir-sync`
**Content-Type**: `text/plain`
**Response**: `200 OK` with JSON Result (blocks thread).

### 3. FHIR to HL7 v2
**Endpoint**: `POST /api/convert/fhir-to-v2`
**Content-Type**: `application/json`
**Body**: FHIR Bundle JSON.

**Example Request**:
```json
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "identifier": [ { "value": "12345" } ],
        "name": [ { "family": "SMITH", "given": ["JOHN"] } ],
        "gender": "male"
      }
    }
  ]
}
```

## Configuration
Key `application.properties`:
```properties
# RabbitMQ Connection
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672

# Queue Config
app.rabbitmq.queue=hl7-messages-queue
app.rabbitmq.output-queue=fhir-messages-queue
```

## License
MIT
