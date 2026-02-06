# Configuration Reference

## Table of Contents
- [Application Properties](#application-properties)
- [RabbitMQ Configuration](#rabbitmq-configuration)
- [Database Configuration](#database-configuration)
- [Security Configuration](#security-configuration)
- [Performance Configuration](#performance-configuration)
- [OpenAPI & Swagger UI Configuration](#openapi--swagger-ui-configuration)
- [Logging Configuration](#logging-configuration)

## Application Properties

All configuration is defined in `src/main/resources/application.properties`. Override via environment variables in production.

### Server Configuration
```properties
# HTTP Port
server.port=8080

# Application Name
spring.application.name=HL7FHIRTransformer

# HTTP/2 Support
server.http2.enabled=true

# Response Compression
server.compression.enabled=true
server.compression.mime-types=application/json,application/fhir+json,text/plain
server.compression.min-response-size=1024
```

**Environment Variable Override**:
```bash
export SERVER_PORT=9090
```

---

## RabbitMQ Configuration

### Connection Settings
```properties
# Broker Connection
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}

# Connection Timeouts
spring.rabbitmq.connection-timeout=10000
spring.rabbitmq.requested-heartbeat=60
```

### Queue Configuration (HL7 → FHIR)
```properties
# Input Queue for HL7 Messages
app.rabbitmq.queue=hl7-messages-queue
app.rabbitmq.exchange=hl7-messages-exchange
app.rabbitmq.routingkey=hl7.message.routing

# Output Queue for FHIR Bundles
app.rabbitmq.output-queue=fhir-messages-queue

# Dead Letter Queue
app.rabbitmq.dlq=hl7-messages-dlq
app.rabbitmq.dlx=hl7-messages-dlx
app.rabbitmq.dl-routingkey=hl7.message.dl
```

### Queue Configuration (FHIR → HL7)
```properties
# Input Queue for FHIR Bundles
app.rabbitmq.fhir.queue=fhir-to-v2-queue
app.rabbitmq.fhir.exchange=fhir-messages-exchange
app.rabbitmq.fhir.routingkey=fhir.message.routing

# Output Queue for HL7 Messages
app.rabbitmq.v2.output-queue=v2-messages-output-queue

# Dead Letter Queue
app.rabbitmq.fhir.dlq=fhir-to-v2-dlq
app.rabbitmq.fhir.dlx=fhir-to-v2-dlx
app.rabbitmq.fhir.dl-routingkey=fhir.message.dl
```

### Performance Tuning
```properties
# Listener Concurrency
spring.rabbitmq.listener.simple.concurrency=5
spring.rabbitmq.listener.simple.max-concurrency=10

# Prefetch Count (messages buffered per consumer)
spring.rabbitmq.listener.simple.prefetch=50

# Reject/Requeue Behavior
spring.rabbitmq.listener.simple.default-requeue-rejected=false

# Connection Pool
spring.rabbitmq.cache.connection.mode=CONNECTION
spring.rabbitmq.cache.channel.size=25
spring.rabbitmq.cache.channel.checkout-timeout=5000
```

**Recommended Production Settings**:
```properties
# High-Throughput (100+ msgs/sec)
spring.rabbitmq.listener.simple.concurrency=10
spring.rabbitmq.listener.simple.max-concurrency=20
spring.rabbitmq.listener.simple.prefetch=100
```

---

## Database Configuration

### MongoDB
```properties
# Connection URI
spring.data.mongodb.uri=${MONGODB_URI:mongodb://mongo:27017/HL7FHIRTransformer}

# Auto-Index Creation (creates indexes on @Indexed fields)
spring.data.mongodb.auto-index-creation=true
```

**Production MongoDB with Authentication**:
```properties
spring.data.mongodb.uri=mongodb://username:password@prod-mongo-cluster:27017,prod-mongo-cluster:27018,prod-mongo-cluster:27019/HL7FHIRTransformer?replicaSet=rs0&authSource=admin
```

### Redis (Caching)
```properties
# Connection
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=2000ms

# Connection Pool (Lettuce)
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=2

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000  # 1 hour (milliseconds)
spring.cache.redis.cache-null-values=false
```

**Cache TTL Override**:
```properties
# Set to 2 hours
spring.cache.redis.time-to-live=7200000
```

**Redis Cluster**:
```properties
spring.data.redis.cluster.nodes=redis-node1:6379,redis-node2:6379,redis-node3:6379
spring.data.redis.cluster.max-redirects=3
```

---

## Security Configuration

### Admin Credentials
```properties
# Default Admin User (ROLE_ADMIN)
app.admin.username=${ADMIN_USERNAME:admin}
app.admin.password=${ADMIN_PASSWORD:password}
```

> ⚠️ **CRITICAL**: Always override in production:
```bash
export ADMIN_USERNAME=secureAdmin
export ADMIN_PASSWORD='C0mpl3x!P@ssw0rd'
```

### Spring Security
Configured in `SecurityConfig.java`:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http
        .csrf(csrf -> csrf.disable())  // Disable for stateless API
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/**").hasRole("ADMIN")
            .requestMatchers("/api/tenants", "/api/tenants/**").hasRole("ADMIN")
            .requestMatchers("/api/convert/**").hasAnyRole("ADMIN", "TENANT")
            .anyRequest().authenticated()
        )
        .httpBasic(withDefaults());
    return http.build();
}
```

**RBAC Rules**:
| Endpoint Pattern | Required Role |
|------------------|---------------|
| `/actuator/**` | ADMIN |
| `/api/tenants/**` | ADMIN |
| `/api/convert/**` | ADMIN or TENANT |

### Password Encoding
- **Algorithm**: BCrypt
- **Strength**: 10 rounds (default)
- **Salt**: Auto-generated per password

---

## Performance Configuration

### Thread Pool (Async Operations)
```properties
# Async Task Execution
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20
spring.task.execution.pool.queue-capacity=100
spring.task.execution.thread-name-prefix=async-
```

**High-Load Tuning**:
```properties
spring.task.execution.pool.core-size=20
spring.task.execution.pool.max-size=50
```

### Tomcat Embedding Server
```properties
# Connection Timeouts
server.tomcat.connection-timeout=20000
server.tomcat.keep-alive-timeout=60000

# Max Connections
server.tomcat.max-connections=10000

# Thread Pool
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=10
```

**Production Tuning** (for 1000+ concurrent users):
```properties
server.tomcat.max-connections=20000
server.tomcat.threads.max=500
server.tomcat.threads.min-spare=50
```

### Caching Strategy

**Cache Names** (defined in `@Cacheable` annotations):
- `tenant`: Tenant lookup by `tenantId` (TTL: 1 hour)
- `transaction`: Transaction lookup by `transactionId` (TTL: 1 hour)
- `tenantStatusCounts`: Status aggregation (TTL: 5 minutes)

**Custom TTL per Cache**:
```java
@Bean
public RedisCacheConfiguration cacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1))
        .disableCachingNullValues();
}

@Bean
public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
    return (builder) -> builder
        .withCacheConfiguration("tenantStatusCounts",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)));
}
```

#### **Virtual Threads (Java 21)**
Enabled by default for high-concurrency services:
- **Batch Processing**: Automatically uses virtual threads to scale conversion tasks.
- **Webhook Notifications**: Uses parallel streams for non-blocking subscriber notification.

#### **Validation Tuning**
- **Singleton Validation Chain**: Configured as a Spring Bean to avoid repeated initialization costs.
- **Cached Schemas**: Validation support chain caches FHIR profiles in-memory.

---

## OpenAPI & Swagger UI Configuration

SpringDoc OpenAPI provides auto-generated documentation.

### Properties
```properties
# Enable/Disable Swagger UI
springdoc.swagger-ui.enabled=true

# Path to Swagger UI
springdoc.swagger-ui.path=/swagger-ui.html

# Selection of API groups
springdoc.api-docs.path=/v3/api-docs

# Enable operations sorting
springdoc.swagger-ui.operationsSorter=method
```

### Security Requirement
Authenticated via Basic Auth. Credentials for Swagger UI match the application admin credentials defined in `app.admin.username` and `app.admin.password`.

---

## Logging Configuration

### Log Levels
```properties
# Application Logs
logging.level.com.HL7FHIRTransformer=${LOG_LEVEL:INFO}

# Spring Security Logs (reduce noise)
logging.level.org.springframework.security=${SECURITY_LOG_LEVEL:WARN}

# Log Pattern
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

**Environment-Specific Levels**:
```bash
# Development
export LOG_LEVEL=DEBUG

# Production
export LOG_LEVEL=WARN
export SECURITY_LOG_LEVEL=ERROR
```

### External Logging (Logback)
Create `src/main/resources/logback-spring.xml`:
```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/HL7FHIRTransformer/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/HL7FHIRTransformer/application-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

---

## Actuator Configuration

### Exposed Endpoints
```properties
# Expose Health, Metrics, Prometheus
management.endpoints.web.exposure.include=health,info,metrics,prometheus

# Show Detailed Health
management.endpoint.health.show-details=always

# Enable Prometheus Metrics Export
management.metrics.export.prometheus.enabled=true
```

**Available Endpoints**:
| Endpoint | URL | Description |
|----------|-----|-------------|
| Health | `/actuator/health` | Aggregated health status |
| Metrics | `/actuator/metrics` | Micrometer metrics |
| Prometheus | `/actuator/prometheus` | Prometheus scrape endpoint |
| Info | `/actuator/info` | Application metadata |

**Health Check Response**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP", ...},
    "mongo": {"status": "UP", "details": {"version": "7.0"}},
    "ping": {"status": "UP"},
    "rabbit": {"status": "UP", "details": {"version": "3.12"}},
    "redis": {"status": "UP", "details": {"version": "7.2"}}
  }
}
```

---

## Environment-Specific Profiles

### Create `application-prod.properties`
```properties
# Production-specific overrides
logging.level.com.HL7FHIRTransformer=WARN
spring.cache.redis.time-to-live=7200000
server.tomcat.threads.max=500
```

### Activate Profile
```bash
# Via environment variable
export SPRING_PROFILES_ACTIVE=prod

# Via command-line argument
java -jar app.jar --spring.profiles.active=prod

# In Docker
docker run -e SPRING_PROFILES_ACTIVE=prod HL7FHIRTransformer
```

---

## Complete Configuration Example

### Production `application.properties`
```properties
# Server
server.port=8080
server.http2.enabled=true
server.compression.enabled=true

# MongoDB
spring.data.mongodb.uri=${MONGODB_URI}
spring.data.mongodb.auto-index-creation=true

# RabbitMQ
spring.rabbitmq.host=${RABBITMQ_HOST}
spring.rabbitmq.port=5672
spring.rabbitmq.username=${RABBITMQ_USERNAME}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
spring.rabbitmq.listener.simple.concurrency=10
spring.rabbitmq.listener.simple.max-concurrency=20
spring.rabbitmq.listener.simple.prefetch=100

# Redis
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=6379
spring.data.redis.password=${REDIS_PASSWORD}
spring.cache.type=redis
spring.cache.redis.time-to-live=7200000

# Security
app.admin.username=${ADMIN_USERNAME}
app.admin.password=${ADMIN_PASSWORD}

# Logging
logging.level.com.HL7FHIRTransformer=WARN
logging.level.org.springframework.security=ERROR

# Performance
spring.task.execution.pool.max-size=50
server.tomcat.threads.max=500
server.tomcat.max-connections=20000

# Actuator
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=always
```

### Docker Compose Environment Variables
```yaml
services:
  HL7FHIRTransformer:
    image: HL7FHIRTransformer:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - MONGODB_URI=mongodb://mongo:27017/HL7FHIRTransformer
      - RABBITMQ_HOST=rabbitmq
      - RABBITMQ_USERNAME=admin
      - RABBITMQ_PASSWORD=${RABBITMQ_ADMIN_PASS}
      - REDIS_HOST=redis
      - ADMIN_USERNAME=${APP_ADMIN_USER}
      - ADMIN_PASSWORD=${APP_ADMIN_PASS}
      - LOG_LEVEL=INFO
```

### Kubernetes ConfigMap + Secret
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: HL7FHIRTransformer-config
data:
  RABBITMQ_HOST: "rabbitmq-service"
  REDIS_HOST: "redis-service"
  LOG_LEVEL: "INFO"
---
apiVersion: v1
kind: Secret
metadata:
  name: HL7FHIRTransformer-secrets
type: Opaque
stringData:
  MONGODB_URI: "mongodb://username:password@mongo-cluster:27017/HL7FHIRTransformer"
  ADMIN_USERNAME: "prod-admin"
  ADMIN_PASSWORD: "Str0ng!P@ssw0rd"
  RABBITMQ_USERNAME: "rmq-admin"
  RABBITMQ_PASSWORD: "rmq-secret"
```

---

## Configuration Validation

### Verify Configuration at Startup
```bash
# Check logs for configuration binding
docker logs HL7FHIRTransformer | grep "Started HL7FHIRTransformerApplication"

# Verify MongoDB connection
docker logs HL7FHIRTransformer | grep "Cluster created with settings"

# Verify RabbitMQ connection
docker logs HL7FHIRTransformer | grep "Created new connection"

# Verify Redis connection
docker logs HL7FHIRTransformer | grep "Lettuce"
```

### Test Configuration via Actuator
```bash
# Check environment variables
curl http://localhost:8090/actuator/env -u admin:password

# Check configuration properties
curl http://localhost:8090/actuator/configprops -u admin:password
```

---

## Next Steps

- **For deployment**, see [Setup & Deployment](setup-deployment.md)
- **For architecture**, see [Architecture & Design Patterns](architecture.md)
- **For API usage**, see [API Reference](api-reference.md)

