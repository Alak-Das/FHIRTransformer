# Setup & Deployment Guide

## Table of Contents
- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Docker Deployment](#docker-deployment)
- [Production Deployment](#production-deployment)
- [Environment Configuration](#environment-configuration)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- **Java Development Kit (JDK)**: 21 or higher
  - Download: https://adoptium.net/
  - Verify: `java -version` should show 21.x.x
- **Maven**: 3.9.9 or higher
  - Download: https://maven.apache.org/download.cgi
  - Verify: `mvn -version`
- **Docker**: 20.10+ and Docker Compose 2.x+
  - Download: https://www.docker.com/products/docker-desktop
  - Verify: `docker --version` and `docker-compose --version`
- **IDE** (Recommended): IntelliJ IDEA, Eclipse, or VS Code with Java extensions

### Optional Tools
- **Postman**: For API testing
- **Newman**: CLI for running Postman collections (`npm install -g newman`)
- **MongoDB Compass**: GUI for MongoDB inspection
- **Redis Insight**: GUI for Redis inspection

---

## Local Development Setup

### Option 1: Full Docker Compose (Recommended for Quick Start)

This deploys all services (app + dependencies) in containers.

#### Step 1: Clone Repository
```bash
git clone <repository-url>
cd fhirhl7transformer
```

#### Step 2: Start All Services
```bash
docker-compose up -d
```

This starts:
- **fhirhl7-transformer**: Main application (port 8090)
- **rabbitmq**: Message broker (ports 5672 AMQP, 15672 management UI)
- **mongo**: Database (port 27017)
- **redis**: Cache (port 6379)

#### Step 3: Verify Health
```bash
# Check all containers are running
docker-compose ps

# Expected output:
# fhirhl7-transformer    running    0.0.0.0:8090->8080/tcp
# fhir-mq             running    5672/tcp, 15672/tcp
# fhir-mongo          running    27017/tcp
# fhir-redis          running    0.0.0.0:6379->6379/tcp

# Test health endpoint
curl http://localhost:8090/actuator/health -u admin:password
```

#### Step 4: Test Conversion
```bash
curl -X POST http://localhost:8090/api/convert/v2-to-fhir-sync \
  -H "Content-Type: text/plain" \
  -u admin:password \
  --data "MSH|^~\&|SYS|FAC|REC|FAC|20240119120000||ADT^A01|MSG001|P|2.5
PID|1||12345||Doe^John||19800101|M"
```

#### Step 5: Access Management UIs
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **Application**: http://localhost:8090

#### Stop Services
```bash
docker-compose down  # Stop containers
docker-compose down -v  # Stop and remove volumes (clears data)
```

---

### Option 2: Hybrid (Local App + Dockerized Dependencies)

Run the Spring Boot app locally for development, with dependencies in Docker.

#### Step 1: Start Only Dependencies
```bash
# Modify docker-compose.yml to comment out fhirhl7-transformer service
# OR use this command to start only specific services:
docker-compose up -d rabbitmq mongo redis
```

#### Step 2: Configure Application Properties
Edit `src/main/resources/application.properties`:
```properties
# Use localhost since dependencies are in Docker
spring.rabbitmq.host=localhost
spring.data.mongodb.uri=mongodb://localhost:27017/fhirhl7transformer
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

#### Step 3: Build and Run Application
```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/fhirhl7-transformer-0.0.1-SNAPSHOT.jar

# OR use Maven Spring Boot plugin
mvn spring-boot:run
```

#### Step 4: Verify
```bash
curl http://localhost:8080/actuator/health -u admin:password
```

**Note**: Application runs on port **8080** (not 8090 as in Docker).

---

### Option 3: IDE Configuration (IntelliJ IDEA)

#### Step 1: Import Project
1. Open IntelliJ IDEA
2. File → Open → Select `pom.xml`
3. Wait for Maven import to complete

#### Step 2: Configure Run Configuration
1. Run → Edit Configurations
2. Add New → Spring Boot
3. Main class: `com.fhirhl7transformer.fhirhl7transformerApplication`
4. VM Options: `-Dspring.profiles.active=dev`
5. Environment Variables:
   ```
   MONGODB_URI=mongodb://localhost:27017/fhirhl7transformer
   RABBITMQ_HOST=localhost
   REDIS_HOST=localhost
   ```

#### Step 3: Start Dependencies
```bash
docker-compose up -d rabbitmq mongo redis
```

#### Step 4: Run Application
Click "Run" in IntelliJ or press `Shift + F10`

---

## Docker Deployment

### Multi-Stage Dockerfile Explained

The project uses a **multi-stage build** for optimized image size:

```dockerfile
# Stage 1: Build (Maven + JDK 21)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline  # Cache dependencies
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime (JRE 21 only)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/fhirhl7-transformer-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Benefits**:
- Final image only contains JRE + JAR (no Maven, source code)
- Image size: ~250MB (vs ~800MB with full JDK)

### Build Custom Image
```bash
docker build -t fhirhl7-transformer:latest .
```

### Run Standalone Container
```bash
docker run -d \
  --name fhirhl7-transformer \
  -p 8090:8080 \
  -e MONGODB_URI=mongodb://host.docker.internal:27017/fhirhl7transformer \
  -e RABBITMQ_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  fhirhl7-transformer:latest
```

---

## Production Deployment

### Kubernetes Deployment

#### Helm Chart (Recommended)

Create `values.yaml`:
```yaml
replicaCount: 3

image:
  repository: your-registry/fhirhl7-transformer
  tag: "1.0.0"
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 8080

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: fhirhl7-transformer.example.com
      paths:
        - path: /
          pathType: Prefix

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

env:
  - name: MONGODB_URI
    value: "mongodb://mongo-service:27017/fhirhl7transformer"
  - name: RABBITMQ_HOST
    value: "rabbitmq-service"
  - name: REDIS_HOST
    value: "redis-service"
  - name: ADMIN_USERNAME
    valueFrom:
      secretKeyRef:
        name: fhirhl7-transformer-secrets
        key: admin-username
  - name: ADMIN_PASSWORD
    valueFrom:
      secretKeyRef:
        name: fhirhl7-transformer-secrets
        key: admin-password

livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

Deploy:
```bash
helm install fhirhl7-transformer ./helm-chart -f values.yaml
```

#### Kubernetes Manifests (Alternative)

**Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fhirhl7-transformer
spec:
  replicas: 3
  selector:
    matchLabels:
      app: fhirhl7-transformer
  template:
    metadata:
      labels:
        app: fhirhl7-transformer
    spec:
      containers:
      - name: fhirhl7-transformer
        image: your-registry/fhirhl7-transformer:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: MONGODB_URI
          valueFrom:
            secretKeyRef:
              name: mongodb-secret
              key: uri
        - name: RABBITMQ_HOST
          value: rabbitmq-service
        - name: REDIS_HOST
          value: redis-service
        resources:
          limits:
            memory: "1Gi"
            cpu: "1000m"
          requests:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
```

**Service**:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: fhirhl7-transformer-service
spec:
  selector:
    app: fhirhl7-transformer
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

Deploy:
```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

---

### AWS Deployment (ECS Fargate)

#### Task Definition
```json
{
  "family": "fhirhl7-transformer",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "fhirhl7-transformer",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/fhirhl7-transformer:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "MONGODB_URI",
          "value": "mongodb://documentdb-cluster:27017/fhirhl7transformer"
        },
        {
          "name": "RABBITMQ_HOST",
          "value": "rabbitmq.example.com"
        },
        {
          "name": "REDIS_HOST",
          "value": "redis-cluster.abc123.cache.amazonaws.com"
        }
      ],
      "secrets": [
        {
          "name": "ADMIN_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:fhir-admin-pass"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/fhirhl7-transformer",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Deploy
```bash
# Register task definition
aws ecs register-task-definition --cli-input-json file://task-definition.json

# Create service
aws ecs create-service \
  --cluster fhir-cluster \
  --service-name fhirhl7-transformer \
  --task-definition fhirhl7-transformer \
  --desired-count 3 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-abc123],securityGroups=[sg-xyz789],assignPublicIp=ENABLED}"
```

---

## Environment Configuration

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8080 | Application HTTP port |
| `MONGODB_URI` | mongodb://mongo:27017/fhirhl7transformer | MongoDB connection string |
| `RABBITMQ_HOST` | localhost | RabbitMQ broker hostname |
| `RABBITMQ_PORT` | 5672 | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | guest | RabbitMQ username |
| `RABBITMQ_PASSWORD` | guest | RabbitMQ password |
| `REDIS_HOST` | localhost | Redis hostname |
| `REDIS_PORT` | 6379 | Redis port |
| `REDIS_PASSWORD` | (empty) | Redis authentication password |
| `ADMIN_USERNAME` | admin | Default admin user |
| `ADMIN_PASSWORD` | password | **CHANGE IN PRODUCTION** |
| `LOG_LEVEL` | INFO | Logging level (DEBUG, INFO, WARN, ERROR) |

### Docker Compose Override

For production, create `docker-compose.prod.yml`:
```yaml
services:
  fhirhl7-transformer:
    environment:
      - ADMIN_USERNAME=${ADMIN_USERNAME}
      - ADMIN_PASSWORD=${ADMIN_PASSWORD}
      - LOG_LEVEL=WARN
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

Deploy:
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## Troubleshooting

### Common Issues

#### 1. Application Fails to Start

**Symptom**: Container exits immediately

**Diagnosis**:
```bash
docker logs fhirhl7-transformer
```

**Common Causes**:
- **MongoDB not ready**: Ensure `depends_on: mongo: condition: service_healthy`
- **Port conflict**: Change `ports: "8091:8080"` if 8090 is in use
- **Memory limit**: Increase `deploy.resources.limits.memory` in `docker-compose.yml`

#### 2. RabbitMQ Connection Refused

**Symptom**: Logs show `Connection refused (Connection refused)`

**Fix**:
```bash
# Check RabbitMQ is running
docker-compose ps rabbitmq

# Restart RabbitMQ
docker-compose restart rabbitmq

# Wait for health check
docker-compose logs -f rabbitmq | grep "Server startup complete"
```

#### 3. MongoDB Authentication Failed

**Symptom**: `MongoSecurityException: Exception authenticating`

**Fix**:
```bash
# Verify connection string
echo $MONGODB_URI

# Test connection manually
docker exec -it fhir-mongo mongosh --eval "db.adminCommand('ping')"
```

#### 4. High Memory Usage

**Symptom**: Application OOMKilled or sluggish

**Fix**:
```bash
# Add JVM memory limits to Dockerfile
ENTRYPOINT ["java", "-Xms512m", "-Xmx1g", "-jar", "app.jar"]

# OR in docker-compose.yml
environment:
  - JAVA_OPTS=-Xms512m -Xmx1g
```

#### 5. Redis Cache Misses

**Symptom**: High MongoDB load, slow response times

**Diagnosis**:
```bash
# Check Redis is reachable
docker exec fhirhl7-transformer redis-cli -h redis ping

# Monitor cache hits
docker exec fhir-redis redis-cli monitor
```

**Fix**:
```bash
# Increase cache TTL in application.properties
spring.cache.redis.time-to-live=7200000  # 2 hours
```

---

### Health Checks

#### Application Health
```bash
curl http://localhost:8090/actuator/health -u admin:password

# Expected:
# {"status":"UP","components":{"mongo":"UP","rabbit":"UP","redis":"UP"}}
```

#### Individual Component Checks
```bash
# MongoDB
docker exec fhir-mongo mongosh --eval "db.runCommand({ping:1})"

# RabbitMQ
docker exec fhir-mq rabbitmq-diagnostics ping

# Redis
docker exec fhir-redis redis-cli ping
```

---

### Performance Tuning

#### JVM Tuning (for high-load scenarios)
```dockerfile
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Xms1g", "-Xmx2g", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-jar", "app.jar"]
```

#### RabbitMQ Tuning
```properties
# application.properties
spring.rabbitmq.listener.simple.concurrency=10
spring.rabbitmq.listener.simple.max-concurrency=20
spring.rabbitmq.listener.simple.prefetch=100
```

#### MongoDB Indexing
```javascript
// Create indexes for faster queries
db.transaction_logs.createIndex({ "tenantId": 1, "timestamp": -1 })
db.transaction_logs.createIndex({ "transactionId": 1 }, { unique: true })
db.tenants.createIndex({ "tenantId": 1 }, { unique: true })
```

---

## Scaling Guidelines

### Horizontal Scaling
1. **Load Balancer**: nginx, HAProxy, or cloud load balancer
2. **Stateless Instances**: No session state stored in app (all in MongoDB/Redis)
3. **Shared RabbitMQ**: All instances connect to same broker
4. **Shared Cache**: Redis cluster for distributed caching

### Vertical Scaling
- **CPU**: Increase for batch processing and parallel conversions
- **Memory**: Increase for larger HL7/FHIR message payloads
- **Disk**: Minimal (application is stateless; data in MongoDB)

### Recommended Configurations

| Deployment Size | App Instances | App Resources | RabbitMQ | MongoDB | Redis |
|-----------------|---------------|---------------|----------|---------|-------|
| **Dev** | 1 | 512MB/0.5 CPU | Single node | Single node | Single node |
| **Staging** | 2 | 1GB/1 CPU | Cluster (3 nodes) | Replica set (3) | Cluster (3) |
| **Production (Small)** | 3 | 2GB/2 CPU | Cluster (3 nodes) | Replica set (5) | Cluster (6) |
| **Production (Large)** | 10+ | 4GB/4 CPU | Cluster (5 nodes) | Sharded cluster | Redis cluster (12+) |

---

## Next Steps

- **For configuration details**, see [Configuration Guide](configuration.md)
- **For architecture**, see [Architecture & Design Patterns](architecture.md)
- **For API reference**, see [API Reference](api-reference.md)

