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
# Base image: flyway/flyway 13.6.0-alpine. NOTE ON VERSION: project practice wants
# the runner version == the flyway-core lg-monteis validated with (Testcontainers +
# jOOQ codegen) — aligned to 13.6.0 in the root pom.
#
# flyway/flyway is a JRE-based image whose default user is root. The migration Jobs'
# pod securityContext forces runAsNonRoot + runAsUser 65532, and readOnlyRootFilesystem:
# true + the emptyDir /tmp mount cover Flyway's temp writes (Flyway must not write
# under /flyway at runtime).
# ─────────────────────────────────────────────────────────────────────────────

# v13.6.0-alpine (comment above FROM statement, otherwise Docker confuses the comment as an arg)
FROM flyway/flyway@sha256:fb7f326765649205574551e25ba774e432ea840b126983d6ec3bbda8f21b137c

# ── Patch OS packages to clear fixed alpine CVEs ────────────────────────────────
USER root
RUN apk --no-cache -U upgrade

# ── Patch the bundled but unused Couchbase driver (CVE-2026-75595 et al.) ───────
# The base image ships com.couchbase.client:{core-io,java-client}:3.12.0, whose
# shaded/vendored Netty is stuck on 4.2.14.Final (CRITICAL CVE-2026-75595 and
# others). We never touch Couchbase, but Flyway's ServiceLoader-based plugin
# registry eagerly class-loads every bundled DatabaseType at startup, so the
# driver can't simply be deleted (breaks `flyway -v` with NoClassDefFoundError).
# Swap in Couchbase's 3.12.3 release instead — same classes, patched Netty
# (4.2.18.Final) — verified by sha256 against the published Maven Central checksum.
RUN set -eux; \
    rm -f /flyway/drivers/couchbase/core-io-3.12.0.jar /flyway/drivers/couchbase/java-client-3.12.0.jar; \
    wget -qO /tmp/core-io.jar https://repo1.maven.org/maven2/com/couchbase/client/core-io/3.12.3/core-io-3.12.3.jar; \
    echo "b62eafaa131ff1dff4d5129153bbf3e00a769bd89e7d530f03586dd618d58c5d  /tmp/core-io.jar" | sha256sum -c -; \
    mv /tmp/core-io.jar /flyway/drivers/couchbase/core-io-3.12.3.jar; \
    wget -qO /tmp/java-client.jar https://repo1.maven.org/maven2/com/couchbase/client/java-client/3.12.3/java-client-3.12.3.jar; \
    echo "4672c6f434f9940062b031985040a554164cef3741c1143f64cba1a11bf540d5  /tmp/java-client.jar" | sha256sum -c -; \
    mv /tmp/java-client.jar /flyway/drivers/couchbase/java-client-3.12.3.jar

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
