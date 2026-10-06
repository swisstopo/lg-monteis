# ── Migration image ────────────────────────────────────────────────────────────
# Bakes the versioned schema SQL files into the stock flyway/flyway image. We migrate
# Postgres only, so every other bundled DB driver is removed below instead of waiving
# its CVEs.
#
# Seed files (db/*/seed) are intentionally EXCLUDED — this is a prod-safe image.
# The Job overrides FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD via env; the
# locations are fixed to the baked paths below.
#
# Base image: flyway/flyway 13.9.0-alpine. NOTE ON VERSION: project practice wants
# the runner version == the flyway-core lg-monteis validated with (Testcontainers +
# jOOQ codegen) — aligned to 13.9.0 in the root pom.
#
# flyway/flyway is a JRE-based image whose default user is root. The migration Jobs'
# pod securityContext forces runAsNonRoot + runAsUser 65532, and readOnlyRootFilesystem:
# true + the emptyDir /tmp mount cover Flyway's temp writes (Flyway must not write
# under /flyway at runtime).
# ─────────────────────────────────────────────────────────────────────────────

# v13.9.0-alpine (comment above FROM statement, otherwise Docker confuses the comment as an arg)
FROM flyway/flyway@sha256:ae8e985a8caf73181a39aad823690f27d55c850d4c5cea92a94c0ce4b3943156

# ── Patch OS packages to clear fixed alpine CVEs ────────────────────────────────
USER root
RUN apk --no-cache -U upgrade

# ── Keep only the Postgres JDBC driver ──────────────────────────────────────────
# we only connect via jdbc:postgresql. the other drivers bundle shaded netty / jackson
# with HIGH CVEs we can't patch, so they go.
# the cassandra, nc-* and databricks plugins load their driver on startup, without it
# flyway crashes (NoClassDefFoundError), so they go too.
# `flyway version` fails the build if a base bump adds another such plugin
RUN find /flyway/drivers -mindepth 1 -maxdepth 1 ! -name 'postgresql-*.jar' -exec rm -rf {} + \
 && rm -f /flyway/lib/flyway/flyway-database-cassandra-*.jar \
          /flyway/lib/flyway/flyway-database-nc-*.jar \
          /flyway/lib/flyway/flyway-database-databricks-*.jar \
 && flyway version

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
