# Database Architecture & Security Setup

Our application uses a dual-database architecture: a standard PostgreSQL database (Meta DB) and a TimescaleDB instance, connected via a Foreign Data Wrapper (FDW) for read-only time-series queries. We enforce strict security by completely separating Data Definition Language (DDL) and Data Manipulation Language (DML) roles.

This document outlines the environment variables that control this setup, the role of Flyway placeholders, and how the configuration adapts across different environments.

---

## Centralized Configuration (Environment Variables)

The database and permission topology is driven by the following environment variables.

### Database Identification

* **META_DB_NAME**: PostgreSQL metadata database name.
* **TS_DB_NAME**: TimescaleDB metrics database name.
* **Why:** Decouples database names from the codebase, enabling dynamic naming across environments.

### Migration Roles (DDL)

* **META_FLYWAY_USER** & **META_FLYWAY_PWD**: Owns the Meta DB schema.
* **TS_FLYWAY_USER** & **TS_FLYWAY_PWD**: Owns the Timescale schema.
* **Why:** These users exclusively execute Flyway migrations. Separating them from application roles ensures runtime applications cannot accidentally or maliciously alter the database structure.

### Application Roles (DML)

* **CORE_APP_META_DB_USER** & **CORE_APP_META_DB_PWD**: Core API user (CRUD on Meta DB, read access to Timescale via FDW).
* **PIPELINE_APP_TS_DB_USER** & **PIPELINE_APP_TS_DB_PWD**: Data ingestion pipeline user (Writes raw metrics directly to TimescaleDB).
* **Why:** Enforces the principle of least privilege. Each application is restricted only to the specific data manipulation operations and databases it requires.

### Foreign Data Wrapper Role (Bridge)

* **FDW_READ_USER** & **FDW_READ_PWD**: Credentials mapped on the Timescale server for the FDW.
* **Why:** When the Core API queries time-series data, the Meta DB uses this role to authenticate against TimescaleDB. It acts as a secure bridge granted absolute minimal, read-only access.

---

## Dynamic Migrations: Flyway Placeholders

* **fdw_read_user**: A variable passed as a placeholder into the TimescaleDB Flyway execution contains the `${FDW_READ_USER}`.
* **Why:** During table creation in TimescaleDB, the migration script must explicitly grant `SELECT` permissions to the FDW bridge user. Flyway safely injects this variable at runtime, keeping SQL files environment-agnostic without hardcoding usernames.

---

## Deployment Scenarios

### 1. Production Environment

* **Initialization:** Databases, base roles, FDW server definitions, and default privileges are provisioned securely via Infrastructure as Code (IaC).
* **Migration:** Flyway runs as a standalone process against the provisioned databases.
* **Required Context:** Requires JDBC URLs, application credentials, Flyway credentials, and the `fdw_read_user` placeholder for the Timescale migration.

### 2. Local Development (Docker Compose)

* **Initialization:** Database containers mount [initialization scripts](../docker/dataset-meta) to their entry points. Docker Compose feeds environment variables into these scripts to dynamically create roles and map the FDW users.
* **Migration:** Dedicated Flyway Docker containers are spun up alongside the databases. Docker Compose passes the environment variables into the Flyway startup commands, authenticating them as the Flyway users and passing the `fdw_read_user` placeholder.

### 3. Integration Testing (Testcontainers & Spring Boot)

* **Initialization:** Testcontainers programmatically spin up isolated PostgreSQL and TimescaleDB instances, mounting the exact same initialization scripts used in local development.
* **Migration:** Spring Boot's default auto-configured Flyway is disabled. Instead, programmatic Java `@Bean` configurations read the environment variables, establish Testcontainer JDBC connections, and execute Flyway manually, injecting the necessary credentials and placeholders.

