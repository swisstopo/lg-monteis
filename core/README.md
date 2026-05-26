# Core API

The **Core API** package acts as the central entrypoint for the business logic of the MONTEIS system.

### Prerequisites
* Java 25
* Maven
* **Docker**: Strictly required for running Integration Tests via `Testcontainers`.

### Local Development
To build the application without running the containerized test suite:
```bash
mvn clean install -DskipTests
```
It is advised to set the active profile to `dev`, in order to get debug logging, API documentation and other development tooling.