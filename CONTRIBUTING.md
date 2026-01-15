# Contributing to FHIR Transformer

Thank you for your interest in contributing to the FHIR Transformer project! This document provides guidelines and instructions for contributing.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Submitting Changes](#submitting-changes)
- [Performance Considerations](#performance-considerations)

---

## Code of Conduct

This project follows a professional code of conduct. Please be respectful and constructive in all interactions.

---

## Getting Started

### Prerequisites

- **Java 21** (Eclipse Temurin recommended)
- **Maven 3.9+**
- **Docker & Docker Compose**
- **Git**
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions

### Fork and Clone

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/FHIRTransformer.git
cd FHIRTransformer

# Add upstream remote
git remote add upstream https://github.com/ORIGINAL_OWNER/FHIRTransformer.git
```

---

## Development Setup

### 1. Start Dependencies

```bash
# Start MongoDB and RabbitMQ
docker-compose up -d fhir-mongo fhir-mq

# Wait for services to be healthy
docker-compose ps
```

### 2. Run Application Locally

```bash
# Compile
mvn clean compile

# Run with DEBUG logging
export LOG_LEVEL=DEBUG
mvn spring-boot:run

# Or run the JAR
mvn clean package
java -jar target/fhir-transformer-0.0.1-SNAPSHOT.jar
```

### 3. Verify Setup

```bash
# Check health
curl -u admin:password http://localhost:8080/actuator/health

# Should return: {"status":"UP"}
```

---

## Project Structure

```
FHIRTransformer/
├── src/main/java/com/fhirtransformer/
│   ├── config/          # Spring configuration classes
│   │   ├── PerformanceConfig.java    # Singleton contexts
│   │   ├── SecurityConfig.java       # RBAC configuration
│   │   └── RabbitMQConfig.java       # Queue setup
│   ├── controller/      # REST API endpoints
│   ├── service/         # Business logic
│   │   ├── FhirToHl7Service.java     # FHIR → HL7 conversion
│   │   ├── Hl7ToFhirService.java     # HL7 → FHIR conversion
│   │   └── AuditService.java         # Audit logging
│   ├── listener/        # RabbitMQ message consumers
│   ├── model/           # Domain models and DTOs
│   ├── repository/      # MongoDB repositories
│   ├── exception/       # Custom exceptions and handlers
│   └── util/            # Utility classes and constants
├── src/main/resources/
│   └── application.properties        # Configuration
├── src/test/           # Unit and integration tests
├── postman/            # Postman collection for testing
├── docker-compose.yml  # Docker services
├── Dockerfile          # Application container
├── README.md           # Main documentation
├── FEATURES.md         # Feature documentation
├── PERFORMANCE.md      # Performance guide
└── pom.xml             # Maven dependencies
```

---

## Coding Standards

### Java Style Guide

- **Formatting**: Follow Google Java Style Guide
- **Naming Conventions**:
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`

### Code Quality

```java
// ✅ Good: Null-safe, clear intent
if (patient.hasIdentifier()) {
    for (Identifier id : patient.getIdentifier()) {
        if (id.hasType() && "MR".equals(id.getType().getCodingFirstRep().getCode())) {
            // Process MRN
        }
    }
}

// ❌ Bad: Potential NPE, unclear
for (Identifier id : patient.getIdentifier()) {
    if (id.getType().getCodingFirstRep().getCode().equals("MR")) {
        // Process MRN
    }
}
```

### Logging

```java
// ✅ Good: Structured, parameterized
log.info("Processing patient: id={}, name={}", patientId, patientName);
log.debug("Mapping {} identifiers for patient {}", identifiers.size(), patientId);

// ❌ Bad: String concatenation, sensitive data
log.info("Processing patient: " + patientId + " with password " + password);
```

### Error Handling

```java
// ✅ Good: Specific exception, helpful message
throw new FhirParsingException("Failed to parse FHIR Bundle: " + e.getMessage(), e);

// ❌ Bad: Generic exception, no context
throw new Exception("Error");
```

---

## Testing Guidelines

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests
newman run postman/FHIR_Transformer.postman_collection.json \
  -e postman/FHIRTransformer.local.postman_environment.json

# Run with coverage
mvn clean test jacoco:report
```

### Writing Tests

#### Unit Tests

```java
@Test
public void testPatientIdentifierPrioritization() {
    // Arrange
    Patient patient = new Patient();
    patient.addIdentifier()
        .setValue("12345")
        .setUse(Identifier.IdentifierUse.USUAL);
    patient.addIdentifier()
        .setValue("MRN-001")
        .setUse(Identifier.IdentifierUse.OFFICIAL)
        .getType().addCoding().setCode("MR");
    
    // Act
    String hl7 = service.convertFhirToHl7(patient);
    
    // Assert
    assertTrue(hl7.contains("PID|1||MRN-001"));  // MRN should be first
}
```

#### Integration Tests (Postman)

Add tests to `postman/FHIR_Transformer.postman_collection.json`:

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Contains expected field", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.resourceType).to.eql("Bundle");
});
```

### Test Coverage Requirements

- **Unit Tests**: Aim for 80%+ coverage on service layer
- **Integration Tests**: All API endpoints must have positive and negative tests
- **All tests must pass** before submitting a PR

---

## Submitting Changes

### 1. Create a Feature Branch

```bash
git checkout -b feature/add-medication-mapping
```

### 2. Make Your Changes

- Write clean, well-documented code
- Follow coding standards
- Add/update tests
- Update documentation if needed

### 3. Test Thoroughly

```bash
# Compile
mvn clean compile

# Run unit tests
mvn test

# Run integration tests
docker-compose up -d
newman run postman/FHIR_Transformer.postman_collection.json \
  -e postman/FHIRTransformer.local.postman_environment.json
```

### 4. Commit with Clear Messages

```bash
git add .
git commit -m "feat: Add MedicationRequest to HL7 RXE mapping

- Implemented bidirectional MedicationRequest ↔ RXE conversion
- Added dosage, frequency, and route mapping
- Updated Postman tests with medication scenarios
- Added documentation to FEATURES.md

Closes #123"
```

**Commit Message Format**:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation only
- `perf:` Performance improvement
- `refactor:` Code refactoring
- `test:` Adding tests
- `chore:` Maintenance tasks

### 5. Push and Create Pull Request

```bash
git push origin feature/add-medication-mapping
```

Then create a Pull Request on GitHub with:
- **Clear title** describing the change
- **Description** explaining what and why
- **Test results** showing all tests pass
- **Screenshots** if UI changes

---

## Performance Considerations

### Always Consider Performance

1. **Reuse Contexts**: Never create new `FhirContext` or `HapiContext` - use injected singletons
2. **Null-Safe Checks**: Use `has*()` methods before accessing FHIR elements
3. **Avoid String Concatenation**: Use `StringBuilder` or parameterized logging
4. **Batch Operations**: Process collections efficiently
5. **Async Where Possible**: Use `@Async` for I/O operations

### Example: Efficient Identifier Processing

```java
// ✅ Good: Single pass, minimal allocations
List<Identifier> identifiers = new ArrayList<>(patient.getIdentifier());
identifiers.sort((a, b) -> {
    boolean aOfficial = (a.hasUse() && "official".equals(a.getUse().toCode()))
            || (a.hasType() && a.getType().hasCoding() && "MR".equals(a.getType().getCodingFirstRep().getCode()));
    boolean bOfficial = (b.hasUse() && "official".equals(b.getUse().toCode()))
            || (b.hasType() && b.getType().hasCoding() && "MR".equals(b.getType().getCodingFirstRep().getCode()));
    return Boolean.compare(bOfficial, aOfficial);
});

// ❌ Bad: Multiple passes, inefficient
List<Identifier> official = patient.getIdentifier().stream()
    .filter(id -> "official".equals(id.getUse().toCode()))
    .collect(Collectors.toList());
List<Identifier> others = patient.getIdentifier().stream()
    .filter(id -> !"official".equals(id.getUse().toCode()))
    .collect(Collectors.toList());
official.addAll(others);
```

---

## Adding New Resource Mappings

### Example: Adding DiagnosticReport Support

1. **Update Service Classes**

```java
// In Hl7ToFhirService.java
private void processDiagnosticReport(Terser terser, Bundle bundle, String patientId) {
    // Implementation
}

// In FhirToHl7Service.java
private void mapDiagnosticReport(DiagnosticReport report, Terser terser) {
    // Implementation
}
```

2. **Add Constants**

```java
// In MappingConstants.java
public static final String SYSTEM_DIAGNOSTIC_REPORT = "http://loinc.org";
```

3. **Add Tests**

```javascript
// In Postman collection
pm.test("Contains DiagnosticReport", function () {
    var bundle = pm.response.json();
    var reports = bundle.entry.filter(e => e.resource.resourceType === "DiagnosticReport");
    pm.expect(reports.length).to.be.above(0);
});
```

4. **Update Documentation**

- Add mapping table to `README.md`
- Update feature list in `FEATURES.md`

---

## Questions or Issues?

- **Bug Reports**: Open an issue with detailed reproduction steps
- **Feature Requests**: Open an issue describing the use case
- **Questions**: Check existing issues or open a new discussion

Thank you for contributing to FHIR Transformer! 🎉
