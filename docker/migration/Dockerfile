# ── Migration image ────────────────────────────────────────────────────────────
# Bakes the versioned schema SQL files into the flyway/flyway base image, then STRIPS
# every DB driver and transport bundle except PostgreSQL. We migrate Postgres only;
# the shipped flyway/flyway image is a kitchen sink (cassandra, couchbase, mongo,
# mssql, aws, gcp, azure drivers) that drags in netty, httpcore5, jackson and
# mssql-jdbc HIGH CVEs for code this Job never executes. Trivy scans jars on disk
# regardless of use, so removing the unused jars removes the findings.
#
# Seed files (db/*/seed) are intentionally EXCLUDED — this is a prod-safe image.
# The Job overrides FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD via env; the
# locations are fixed to the baked paths below.
#
# Base image: flyway/flyway 12.11.0-alpine. NOTE ON VERSION: project practice wants
# the runner version == the flyway-core lg-monteis validated with (Testcontainers +
# jOOQ codegen). If this stays at 12.x-newer than the backend's flyway-core, bump
# flyway-core in lg-monteis and re-run the backend build so runner == validator.
#
# WHY NOT DISTROLESS: the HIGH findings are in the JVM jars (21), not the OS (3), so
# a distroless base would carry the same jars and not move the trivy gate — while
# breaking the shell entrypoint the migration Jobs rely on. The fix is jar-stripping.
#
# flyway/flyway is a JRE-based image whose default user is root. The migration Jobs'
# pod securityContext forces runAsNonRoot + runAsUser 65532, and readOnlyRootFilesystem:
# true + the emptyDir /tmp mount cover Flyway's temp writes (Flyway must not write
# under /flyway at runtime).
# ─────────────────────────────────────────────────────────────────────────────

# v12.11.0-alpine (comment above FROM statement, otherwise Docker confuses the comment as an arg)
FROM flyway/flyway@sha256:bd93084ddaf1448d2598feaac75a1c2e7087c529566746724f65b05b481f9704

# ── Patch OS packages to clear fixed alpine CVEs ────────────────────────────────
# The alpine base carries a handful of HIGH OS-package CVEs (libexpat CVE-2026-56408,
# p11-kit / p11-kit-trust CVE-2026-2100) that already have fixed -rN packages in the
# 3.23 branch. Upgrade to pull them; run as root before the securityContext drops.
USER root
RUN apk --no-cache -U upgrade

# ── Strip everything except the PostgreSQL connector ────────────────────────────
# Flyway 12 registers every bundled DB connector as a ServiceLoader Plugin from a
# flyway-database-*/flyway-<db>-* jar in /flyway/lib/flyway. A plugin whose driver
# libs are missing fails plugin registration at startup (e.g. nc-mongodb -> missing
# org.bson.conversions.Bson). So the driver jars AND their matching plugin jars must
# be removed together. We migrate Postgres only.
#
# 1) /flyway/lib/flyway: WHITELIST — keep the engine (commandline, core, nc-core /
#    nc-callbacks / nc-scanners), all verbs (migrate/info/validate/...), reports, the
#    command plugins, and the postgresql connector. Delete every other DB connector.
# 2) /flyway/drivers: keep postgresql-*.jar only (removes mssql/oracle/mariadb/db2/
#    snowflake/sqlite/h2/hsqldb/... + the aws/cassandra/couchbase/gcp/mongo subdirs
#    and their httpcore5/core-io/netty/jackson HIGH-CVE baggage).
# 3) /flyway/lib/{netty,aad}: netty transport + Azure AD auth — orphaned once the
#    cloud/mongo/couchbase/sqlserver connectors are gone (source of the netty HIGHs).
# Verified with a `flyway info` smoke run at build time before push.
RUN set -eux; \
    cd /flyway/lib/flyway; \
    for f in *.jar; do \
      case "$f" in \
        flyway-commandline-*|flyway-core-*|flyway-nc-core-*|flyway-nc-callbacks-*|flyway-nc-scanners-*|flyway-verb-*|flyway-reports-*|flyway-command-*|flyway-database-postgresql-*) : ;; \
        *) rm -f "$f" ;; \
      esac; \
    done; \
    cd /flyway/drivers; \
    find . -mindepth 1 -maxdepth 1 ! -name 'postgresql-*.jar' ! -name 'put-your-jdbc-drivers-here.txt' -exec rm -rf {} +; \
    rm -rf /flyway/lib/netty /flyway/lib/aad; \
    echo "kept connectors:"; ls -1 /flyway/lib/flyway | grep -E 'database|verb|nc-|core|command|reports'; \
    echo "kept drivers:"; ls -1 /flyway/drivers

# ── Copy schema-only SQL files ─────────────────────────────────────────────────
# db/meta/schema  → /flyway/sql/meta  (RDS Meta DB versioned migrations V__*.sql)
# db/timescale/schema → /flyway/sql/ts (TimescaleDB versioned migrations V__*.sql)
# db/*/seed is deliberately NOT copied (Q3 decision: one prod-safe image).
COPY db/meta/schema /flyway/sql/meta
COPY db/timescale/schema /flyway/sql/ts

# ── Drop back to a non-root user ────────────────────────────────────────────────
# apk (above) needed root; nothing at runtime does. Reset to 65532 so the image
# default matches the pod securityContext (runAsNonRoot / runAsUser 65532) and is
# safe if ever run outside the hardened pod. Flyway writes only to /tmp (emptyDir);
# rootfs is read-only. Numeric UID keeps runAsNonRoot happy (no username lookup).
USER 65532

# Inherit the flyway/flyway ENTRYPOINT and CMD — Jobs override via env vars:
#   FLYWAY_URL, FLYWAY_USER, FLYWAY_PASSWORD, FLYWAY_LOCATIONS
