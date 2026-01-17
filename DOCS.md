# 📚 FHIR Transformer - Complete Documentation Index

Welcome to the FHIR Transformer documentation! This index will help you find the information you need.

## 🚀 Quick Start

**New to the project?** Start here:
1. **[README.md](README.md)** - Overview, quick start, API reference
2. **[Docker Compose Setup](#quick-deployment)** - Get running in 2 minutes
3. **[Postman Tests](#testing)** - Verify your installation

## 📖 Core Documentation

### For Users & Operators

| Document | Description | When to Read |
|:---------|:------------|:-------------|
| **[README.md](README.md)** | Main documentation: architecture, API reference, quick start | **Start here** - First time setup |
| **[FEATURES.md](FEATURES.md)** | Enterprise features, use cases, deployment checklist | Before production deployment |
| **[PERFORMANCE.md](PERFORMANCE.md)** | Performance optimizations, benchmarks, tuning guide | When optimizing for scale |
| **[CHANGELOG.md](CHANGELOG.md)** | Version history and release notes | When upgrading versions |
| **[ROADMAP.md](ROADMAP.md)** | Future improvements and enhancement opportunities | Planning next features |

### For Developers

| Document | Description | When to Read |
|:---------|:------------|:-------------|
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | Development setup, coding standards, contribution workflow | Before making code changes |
| **[Postman Collection](postman/)** | 105 integration tests across 33 scenarios | For testing and API examples |

## 🎯 Documentation by Task

### I want to...

#### Deploy to Production
1. Read **[FEATURES.md](FEATURES.md)** - Deployment Checklist section
2. Review **[PERFORMANCE.md](PERFORMANCE.md)** - Production Deployment Recommendations
3. Check **[README.md](README.md)** - Security Architecture and Environment Variables
4. Run integration tests from **[Postman Collection](postman/)**

#### Optimize Performance
1. Read **[PERFORMANCE.md](PERFORMANCE.md)** - Complete performance guide
2. Check **[README.md](README.md)** - Quick Reference section
3. Review **application.properties** - Tuning parameters

#### Understand the Mappings
1. Read **[README.md](README.md)** - Advanced Mapping Features section
2. Check **[FEATURES.md](FEATURES.md)** - Detailed mapping tables
3. Review **[MappingConstants.java](src/main/java/com/fhirtransformer/util/MappingConstants.java)** - System URLs and codes

#### Contribute Code
1. Read **[CONTRIBUTING.md](CONTRIBUTING.md)** - Complete developer guide
2. Review **[README.md](README.md)** - Project Structure
3. Check **[CHANGELOG.md](CHANGELOG.md)** - Recent changes
4. Run tests from **[Postman Collection](postman/)**

#### Troubleshoot Issues
1. Check **[README.md](README.md)** - Common Troubleshooting section
2. Review **[PERFORMANCE.md](PERFORMANCE.md)** - Performance Troubleshooting
3. Check Docker logs: `docker-compose logs fhir-transformer`
4. Review **[Postman Collection](postman/)** - Test scenarios

#### Monitor in Production
1. Read **[README.md](README.md)** - Key Metrics to Monitor
2. Check **[PERFORMANCE.md](PERFORMANCE.md)** - Monitoring & Profiling section
3. Review **[FEATURES.md](FEATURES.md)** - Observability features

## 📁 File Structure

```
FHIRTransformer/
├── 📄 README.md              ⭐ Start here - Main documentation
├── 📄 FEATURES.md            🎯 Enterprise features & use cases
├── 📄 PERFORMANCE.md         ⚡ Performance guide & benchmarks
├── 📄 CONTRIBUTING.md        👥 Developer contribution guide
├── 📄 CHANGELOG.md           📋 Version history
├── 📄 Z_SEGMENT_SUPPORT.md   🧩 Z-Segment features
├── 📄 DOCS.md                📚 This file - Documentation index
├── 📄 LICENSE                ⚖️ MIT License
├── 📄 pom.xml                🔧 Maven dependencies
├── 📄 Dockerfile             🐳 Application container
├── 📄 docker-compose.yml     🐳 Multi-container setup
│
├── 📂 src/main/java/com/fhirtransformer/
│   ├── config/               ⚙️ Spring configuration
│   ├── controller/           🌐 REST API endpoints
│   ├── service/              💼 Business logic
│   ├── listener/             📨 RabbitMQ consumers
│   ├── model/                📊 Domain models & DTOs
│   ├── repository/           💾 MongoDB repositories
│   ├── exception/            ⚠️ Error handling
│   └── util/                 🛠️ Utilities & constants
│
├── 📂 src/main/resources/
│   └── application.properties 🔧 Configuration file
│
├── 📂 src/test/              ✅ Unit tests
│
└── 📂 postman/               🧪 Integration tests
    ├── FHIR_Transformer.postman_collection.json
    └── FHIRTransformer.local.postman_environment.json
```

## 🔍 Quick Reference

### Quick Deployment

```bash
# Clone and start
git clone <repository-url>
cd FHIRTransformer
docker-compose up -d

# Verify
curl -u admin:password http://localhost:9091/actuator/health
```

### Testing

```bash
# Run integration tests
newman run postman/FHIR_Transformer.postman_collection.json \
  -e postman/FHIRTransformer.local.postman_environment.json

# Expected: 105/105 assertions passing
```

### Key Configuration

```properties
# application.properties
LOG_LEVEL=INFO                              # Production logging
server.http2.enabled=true                   # HTTP/2 support
server.compression.enabled=true             # GZIP compression
spring.rabbitmq.listener.simple.max-concurrency=10  # Consumer threads
server.tomcat.threads.max=200               # Request threads
```

### Environment Variables

```bash
# Production secrets
export ADMIN_PASSWORD="your-secure-password"
export RABBITMQ_PASSWORD="your-rabbitmq-password"
export MONGODB_URI="mongodb://user:pass@host:27017/db"
export LOG_LEVEL="INFO"
```

## 📊 Documentation Statistics

- **Total Documentation Files**: 7 (README, FEATURES, PERFORMANCE, CONTRIBUTING, CHANGELOG, DOCS, Z_SEGMENT_SUPPORT)
- **Total Pages**: ~50 pages of comprehensive documentation
- **Code Comments**: Extensive inline documentation
- **Test Documentation**: 33 integration tests with detailed assertions
- **API Endpoints Documented**: 11 endpoints with examples
- **Mapping Tables**: 6 comprehensive mapping tables
- **Performance Benchmarks**: 8 key metrics documented
- **Configuration Options**: 30+ tunable parameters

## 🎓 Learning Path

### Beginner (Day 1)
1. Read **README.md** - Overview and Quick Start
2. Deploy with Docker Compose
3. Run Postman tests
4. Explore API with sample requests

### Intermediate (Week 1)
1. Read **FEATURES.md** - Understand all capabilities
2. Review **application.properties** - Configuration options
3. Study mapping tables in README
4. Experiment with different HL7/FHIR messages

### Advanced (Month 1)
1. Read **PERFORMANCE.md** - Optimization techniques
2. Read **CONTRIBUTING.md** - Code architecture
3. Review source code - Service implementations
4. Contribute improvements or new features

### Expert (Ongoing)
1. Monitor production metrics
2. Tune performance for your workload
3. Extend with new resource mappings
4. Share knowledge with community

## 🆘 Getting Help

### Documentation Not Clear?
1. Check all related documents (use this index)
2. Search for keywords in documentation
3. Review code examples in Postman collection
4. Check inline code comments

### Found a Bug?
1. Check **[CHANGELOG.md](CHANGELOG.md)** - Known limitations
2. Review **[README.md](README.md)** - Troubleshooting section
3. Check existing GitHub issues
4. Open new issue with reproduction steps

### Feature Request?
1. Check **[CHANGELOG.md](CHANGELOG.md)** - Planned features
2. Review **[FEATURES.md](FEATURES.md)** - Current capabilities
3. Open GitHub issue describing use case

### Want to Contribute?
1. Read **[CONTRIBUTING.md](CONTRIBUTING.md)** - Complete guide
2. Fork repository and create feature branch
3. Write tests for your changes
4. Submit pull request

## 📈 Documentation Maintenance

This documentation is actively maintained. Last updated: **2026-01-17**

### Documentation Standards
- **Keep it Current**: Update docs with code changes
- **Be Comprehensive**: Cover all features and use cases
- **Stay Practical**: Include real examples and commands
- **Think User-First**: Organize by user needs, not code structure

### Contributing to Docs
Documentation improvements are always welcome! See **[CONTRIBUTING.md](CONTRIBUTING.md)** for guidelines.

---

## 🎉 Ready to Get Started?

**New Users**: Start with **[README.md](README.md)**  
**Deploying**: Check **[FEATURES.md](FEATURES.md)** deployment checklist  
**Optimizing**: Read **[PERFORMANCE.md](PERFORMANCE.md)**  
**Developing**: Follow **[CONTRIBUTING.md](CONTRIBUTING.md)**  

**Questions?** All documentation is searchable - use Ctrl+F to find what you need!

---

*This documentation index is part of the FHIR Transformer project - A production-ready, enterprise-grade HL7 ↔ FHIR transformation service.*
