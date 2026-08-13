# ── Migration image ────────────────────────────────────────────────────────────
# Bakes the versioned schema SQL files into the flyway/flyway base image.
# Seed files (db/*/seed) are intentionally EXCLUDED — this is a prod-safe image.
# The Job overrides FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD via env; the
# locations are fixed to the baked paths below.
#
# Base image: flyway/flyway 12.4.0 — the EXACT Flyway version lg-monteis resolves
# (`mvn -pl core dependency:tree | grep flyway-core` -> org.flywaydb:flyway-core:12.4.0,
# test-scoped, via spring-boot-starter-parent 3.5.16). The migration runner MUST match
# the version that validated the migrations (Testcontainers) + feeds jOOQ codegen —
# a major/minor mismatch risks parser/checksum/behaviour skew.
#
# NOTE: flyway/flyway is a JRE-based image (NOT distroless) whose default user is root.
# The migration Jobs' pod securityContext forces runAsNonRoot + runAsUser 65532, and
# readOnlyRootFilesystem: true + the emptyDir /tmp mount cover Flyway's temp writes
# (Flyway must not write under /flyway at runtime).
# ─────────────────────────────────────────────────────────────────────────────

FROM flyway/flyway:sha256:0d37ec97d373da37d31a96ff65f86ac8394ba9a003fc3c938de0c1721f523ec2 # v12.4.0 (matches lg-monteis flyway-core)

# ── Copy schema-only SQL files ─────────────────────────────────────────────────
# db/meta/schema  → /flyway/sql/meta  (RDS Meta DB versioned migrations V__*.sql)
# db/timescale/schema → /flyway/sql/ts (TimescaleDB versioned migrations V__*.sql)
# db/*/seed is deliberately NOT copied (Q3 decision: one prod-safe image).
COPY db/meta/schema /flyway/sql/meta
COPY db/timescale/schema /flyway/sql/ts

# Inherit the flyway/flyway ENTRYPOINT and CMD — Jobs override via env vars:
#   FLYWAY_URL, FLYWAY_USER, FLYWAY_PASSWORD, FLYWAY_LOCATIONS
