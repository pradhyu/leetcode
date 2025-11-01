# Java Microservice Utilities

A comprehensive Java microservice utilities project that provides reusable components, examples, and best practices for building modern REST-based microservices that deploy seamlessly to Kubernetes.

## Overview

This project provides a complete toolkit for Java microservice development including:

- **Core Utilities**: REST services, caching, data access, security
- **Infrastructure Utilities**: API gateway, observability, health monitoring
- **Integration Utilities**: HTTP clients, file processing, event handling
- **Platform Utilities**: Testing, migration, async processing, feature flags
- **Example Services**: Complete microservice implementations
- **Cloud-Native Deployment**: Kubernetes manifests, Helm charts, CI/CD pipelines

## Project Structure

```
java-microservice-utilities/
├── utilities/                  # Reusable utility modules
│   ├── common-utils/          # Common utilities and base classes
│   ├── rest-utils/            # REST service utilities
│   ├── cache-utils/           # Multi-layer caching utilities
│   ├── orm-utils/             # JPA/Hibernate utilities
│   ├── jdbc-utils/            # JDBC utilities
│   ├── security-utils/        # Security and authentication utilities
│   ├── gateway-utils/         # API gateway utilities
│   ├── observability-utils/   # Monitoring and tracing utilities
│   ├── integration-utils/     # Integration and messaging utilities
│   └── platform-utils/        # Platform and testing utilities
├── examples/                   # Example microservices
│   ├── user-service/          # User management service
│   ├── order-service/         # Order processing service
│   └── gateway-service/       # API gateway service
├── deployment/                 # Deployment configurations
│   ├── kubernetes/            # Kubernetes manifests
│   ├── helm/                  # Helm charts
│   └── docker/                # Docker configurations
└── docs/                      # Documentation
```

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- Docker (for containerization)
- Kubernetes cluster (for deployment)

### Build the Project

```bash
# Clone the repository
git clone <repository-url>
cd java-microservice-utilities

# Build all modules
mvn clean install

# Build with code quality checks
mvn clean install -Pcode-quality
```

### Run Example Services

```bash
# Start User Service
cd examples/user-service
mvn spring-boot:run

# Start Order Service
cd examples/order-service
mvn spring-boot:run

# Start Gateway Service
cd examples/gateway-service
mvn spring-boot:run
```

## Features

### Core Utilities

- **REST Utils**: Standardized REST controllers, exception handling, OpenAPI documentation
- **Cache Utils**: Multi-layer caching with Caffeine (L1) and Redis (L2)
- **ORM Utils**: JPA/Hibernate utilities with auditing and transaction management
- **JDBC Utils**: Raw SQL operations with batch processing and stored procedures
- **Security Utils**: JWT authentication, OAuth2 integration, CORS configuration

### Infrastructure Utilities

- **Gateway Utils**: Request routing, rate limiting, circuit breakers
- **Observability Utils**: OpenTelemetry tracing, Prometheus metrics, health checks
- **Integration Utils**: HTTP clients, file processing, Kafka messaging
- **Platform Utils**: TestContainers, database migration, async processing

### Cloud-Native Features

- **Container Optimization**: Multi-stage Dockerfiles with distroless images
- **Kubernetes Integration**: Deployment manifests, services, ConfigMaps
- **Helm Charts**: Parameterized deployments for different environments
- **CI/CD Pipelines**: GitHub Actions with GitOps deployment
- **Observability**: Distributed tracing, metrics collection, log aggregation

## Documentation

- [Getting Started Guide](docs/getting-started.md)
- [Architecture Overview](docs/architecture.md)
- [API Documentation](docs/api.md)
- [Deployment Guide](docs/deployment.md)
- [Development Guide](docs/development.md)

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For questions and support, please open an issue in the GitHub repository.