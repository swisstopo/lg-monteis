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

### Code Style & Formatting

This project adheres to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). To ensure consistency, a Git pre-commit hook is configured to automatically format code locally using the Spotless Maven plugin. Additionally, GitHub Actions enforce these formatting standards on all upstream pull requests.
You can format the core package as follows:

```shell
mvn spotless:apply
```

or use the provided run configuration. To validate the format use `check` instead of `apply`.
