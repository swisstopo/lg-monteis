# ── Migration image ────────────────────────────────────────────────────────────
# Bakes the versioned schema SQL files into the stock flyway/flyway image. We migrate
# Postgres only, but the shipped image bundles every DB driver (cassandra, couchbase,
# mongo, mssql, aws, gcp, azure) plus their netty/httpcore5/jackson/mssql-jdbc jars,
# which carry HIGH CVEs for code this Job never executes.
# The non-Postgres CVEs are waived by CVE id in the repo-root `.trivyignore`
#
# Seed files (db/*/seed) are intentionally EXCLUDED — this is a prod-safe image.
# The Job overrides FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD via env; the
# locations are fixed to the baked paths below.
#
# Base image: flyway/flyway 12.11.0-alpine. NOTE ON VERSION: project practice wants
# the runner version == the flyway-core lg-monteis validated with (Testcontainers +
# jOOQ codegen) — aligned to 12.11.0 in the root pom.
#
# flyway/flyway is a JRE-based image whose default user is root. The migration Jobs'
# pod securityContext forces runAsNonRoot + runAsUser 65532, and readOnlyRootFilesystem:
# true + the emptyDir /tmp mount cover Flyway's temp writes (Flyway must not write
# under /flyway at runtime).
# ─────────────────────────────────────────────────────────────────────────────

# v12.11.0-alpine (comment above FROM statement, otherwise Docker confuses the comment as an arg)
FROM flyway/flyway@sha256:bd93084ddaf1448d2598feaac75a1c2e7087c529566746724f65b05b481f9704

# ── Patch OS packages to clear fixed alpine CVEs ────────────────────────────────
USER root
RUN apk --no-cache -U upgrade

# ── Copy schema-only SQL files ─────────────────────────────────────────────────
# db/meta/schema  → /flyway/sql/meta  (RDS Meta DB versioned migrations V__*.sql)
# db/timescale/schema → /flyway/sql/ts (TimescaleDB versioned migrations V__*.sql)
# db/*/seed is deliberately NOT copied (Q3 decision: one prod-safe image).
COPY db/meta/schema /flyway/sql/meta
COPY db/timescale/schema /flyway/sql/ts

# ── Drop back to a non-root user ────────────────────────────────────────────────
# apk needed root; nothing at runtime does. Reset to 65532 so the image default
# matches the pod securityContext (runAsNonRoot / runAsUser 65532) and is safe if run
# outside the hardened pod. Flyway writes only to /tmp (emptyDir); rootfs is read-only.
USER 65532

# Inherit the flyway/flyway ENTRYPOINT and CMD — Jobs override via env vars:
#   FLYWAY_URL, FLYWAY_USER, FLYWAY_PASSWORD, FLYWAY_LOCATIONS
