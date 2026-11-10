# Migrationsplan: Fusion Meta-DB + TimescaleDB

Zusammenführung der zwei Datenbanken (`monteis-meta` + `monteis-tsdb`) in **eine** PostgreSQL/TimescaleDB-Instanz, ein Schema `public`. Verzicht auf FDW; RLS nativ auf `sensor_reading`; zwei App-Rollen (`core`, `pipeline`).

## Ziel-Definition

- Eine Instanz, ein Schema `public`. Lokal/Tests: Image `timescale/timescaledb:latest-pg18`; AWS: CNPG-Cluster `timescaledb` mit `timescale/timescaledb-ha:pg18.4-ts2.28.1`, DB `monteis_ts`.
- **Kein Rollback vorgesehen**: Auf den AWS-Instanzen existieren nur Testdaten.
- **Zielinstanz AWS: bestehende TimescaleDB-Instanz.** Die Meta-DB (klein) wird per Dump/Restore dorthin migriert; `sensor_reading` bleibt liegen (kein Daten-Copy). **RDS bleibt bestehen** (Keycloak-DB `keycloak` liegt dort, `kubernetes/keycloak/base/db-bootstrap-job.yaml`); entfernt wird nur die RDS-DB `monteis`.
- `sensor_reading` als lokale Hypertable, **bewusst ohne FK** `sensor_parameter_id → sensor_parameter.id` (wie heute): Core löscht Parameter (`JooqSensorRepository`), und die Pipeline kann aus ihrem `SensorConfigCache` noch bereits gelöschte IDs schreiben — ein FK würde Löschungen bzw. ganze Ingest-Batches blockieren. Verwaiste IDs sind via RLS unsichtbar; Integrität wird per Monitoring überwacht.
- FDW komplett entfernt (`raw_sensor_reading`, `timescale_server`, `fdw_user`, `postgres_fdw`-Extension).
- RLS nativ auf `sensor_reading`; `sensor_reading_secured` entfällt.
- Rollen (Namen wie in AWS, lokal identisch): `monteis_ts` (Owner/DDL, Flyway; in AWS CNPG-Bootstrap-Owner), `core` (CRUD Meta + SELECT sensor_reading), `pipeline` (I/U/D + SELECT nur sensor_reading). **Keine** Mitgliedschaft in `pg_read_all_data`/`pg_write_all_data` — nur explizite Grants.

---

## Etappe A — Lokales Setup, Anwendung und CI

AWS bleibt während dieser Etappe unverändert. Lokal erfolgt ein frischer Aufbau mit Seeds statt einer Datenübernahme. Bestehende lokale Volumes nur nach ausdrücklicher Freigabe löschen.

## Phase A0 — Lokale Vorbereitung

- [ ] Repräsentative lokale Testdaten und Orphan-Zählwerte für Vorher-/Nachher-Vergleiche festhalten.
- [ ] **Benchmark-Baseline**: `EXPLAIN ANALYZE` der Kernqueries (`findChartData`, Overview `LIMIT 100`, 52k-Range-Scan) gegen den FDW-Pfad festhalten — je mit `read_all` und mit eingeschränktem User.

## Phase A1 — Neue Flyway-Baseline

Strategie: Alle Meta- (V1–V23) und TS-Migrationen (V1–V4) werden zu **einem** Baseline-Skript konsolidiert, das direkt den fusionierten Zielzustand beschreibt (ohne FDW, ohne `sensor_reading_secured`).

- **Neue Struktur**: `db/schema/V1__baseline.sql`, `db/seed/R__seed_dev_data.sql` (Seeds zusammengeführt). Alte Migrationen nach `db/archive/meta` bzw. `db/archive/timescale` (nicht mehr in den Flyway-`locations`).
- **Frische Setups** (Dev, Testcontainers, CI): Flyway führt `V1__baseline.sql` normal aus.

### A1a. Erstellung `V1__baseline.sql`

- [ ] Meta-Teil generieren: frisches Dev-Setup auf Stand V23 → `pg_dump --schema-only --no-owner` → bereinigen: FDW-Objekte (`postgres_fdw`, `timescale_server`, User Mapping, `raw_sensor_reading`, `sensor_reading_secured`) entfernen; Default-Privileges gehören ins Init, nicht in die Baseline.
- [ ] Vollständig erhalten: RLS-Funktionen (`can_read_all_experiments`, `current_experiment_ids`, …), alle Policies, JaVers-Tabellen (Meta-V4), explizite Grants.
- [ ] Zeitreihen-Teil **von Hand** schreiben (nicht aus dem Dump — dieser enthält Timescale-Interna/Chunks), siehe SQL unten.
- [ ] Erklärende Kommentare aus den Alt-Migrationen (Index-Begründungen TS-V1/V3, RLS-Begründungen) in die Baseline übernehmen, soweit weiterhin gültig.

Zeitreihen- und Fusions-Teil der Baseline:

```sql
-- 1. Enum + Tabelle (Spiegel von ts-V1..V4; bewusst ohne FK auf sensor_parameter)
CREATE TYPE range_category AS ENUM ('too_low', 'correct', 'too_high');

CREATE TABLE sensor_reading (
    "timestamp"          TIMESTAMPTZ NOT NULL,
    das_key              VARCHAR(500) NOT NULL,   -- wie TS-V2, sonst Drift zu Prod
    raw_value            DOUBLE PRECISION NOT NULL,
    norm_value           DOUBLE PRECISION,
    version              SMALLINT DEFAULT 0,
    status               range_category,
    sensor_parameter_id  UUID,
    PRIMARY KEY ("timestamp", das_key)
);

CREATE INDEX sensor_reading_das_key_time_idx
    ON sensor_reading (das_key, "timestamp" DESC);
CREATE INDEX sensor_reading_sensor_parameter_time_idx
    ON sensor_reading (sensor_parameter_id, "timestamp" DESC);

SELECT create_hypertable('sensor_reading', 'timestamp', create_default_indexes => FALSE);

-- 2. RLS (ersetzt sensor_reading_secured)
-- core: gleiche Semantik wie der heutige View-Join (V14). Der Subselect auf
-- sensor_parameter läuft als core, dessen RLS (V10, can_access_sensor) filtert
-- transitiv → keine doppelte Logik, Planner kann Semi-Join/Hashed Subplan wählen.
-- NULL/verwaiste sensor_parameter_id matchen nie → unsichtbar wie heute.
ALTER TABLE sensor_reading ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensor_reading_read ON sensor_reading FOR SELECT TO core
    USING (sensor_parameter_id IN (SELECT id FROM sensor_parameter));

-- pipeline: FOR ALL statt getrennter INSERT/UPDATE/DELETE-Policies. Ohne
-- SELECT-Policy sieht pipeline keine Zeilen — das bricht UPDATE … WHERE,
-- INSERT … ON CONFLICT DO UPDATE und die Reprocessing-Selects in
-- SensorReadingRepository (Postgres wendet dort SELECT-Policies mit an).
CREATE POLICY sensor_reading_pipeline ON sensor_reading FOR ALL TO pipeline
    USING (true) WITH CHECK (true);

-- 3. Grants — Default-Privileges würden core sonst Schreibrecht geben!
GRANT SELECT ON sensor_reading TO core;
REVOKE INSERT, UPDATE, DELETE ON sensor_reading FROM core;
GRANT SELECT, INSERT, UPDATE, DELETE ON sensor_reading TO pipeline;
```

## Phase A2 — Init & Compose

- [ ] `docker/dataset-init/init.sql` (ersetzt `dataset-meta` + `dataset-tsdb`): `CREATE EXTENSION timescaledb` (kein postgres_fdw), `CREATE USER monteis_ts`, `core`, `pipeline`, Schema-Grants, `ALTER DEFAULT PRIVILEGES FOR ROLE monteis_ts` für Meta-Tabellen/Sequences → `core`; Zeitreihen-Grants weiterhin pro Migration explizit (Default-Privileges können nicht pro Tabelle).
  - Keine FDW-Grants mehr (`GRANT USAGE ON FOREIGN DATA WRAPPER`/`FOREIGN SERVER` aus `init_meta.sql` entfallen), kein `fdw_user`, kein `CREATE SERVER`/`USER MAPPING`.
  - `pipeline` erhält **keine** `ALTER DEFAULT PRIVILEGES` (heute in `init_tsdb.sql` für alle Tabellen von `ts_flyway`) — nur `USAGE ON SCHEMA public` + explizite Grants auf `sensor_reading` aus der Baseline. Sonst bekäme die Pipeline CRUD auf alle Meta-Tabellen.
  - AWS: Rollen werden von CNPG verwaltet (Phase B1), nicht vom Init.
- [ ] `docker/compose.yml`: `ts_db`- und `flyway-timescale`-Services entfernen; `metadata_db` → Timescale-Image; Ports vereinheitlichen (empfohlen 5432).
- [ ] `.env.example.local`: `TS_DB_NAME`, `TS_FLYWAY_*`, `FDW_*` entfernen; einen `DB_NAME`; `META_FLYWAY_*`/`TS_FLYWAY_*` → `DB_FLYWAY_*` (Rolle `monteis_ts`); `CORE_APP_META_DB_*` → Rolle `core`; `PIPELINE_APP_TS_DB_*` → Rolle `pipeline`.
- [ ] Neue Struktur `db/schema` + `db/seed` (siehe Phase 1); `db/meta/schema` und `db/timescale/schema` nach `db/archive/*`; Seeds zusammenführen; `sensor_reading`-Seed muss auf existierende `sensor_parameter.id` zeigen (sonst für `core` unsichtbar).
- [ ] `flyway-metadata`-Service: `locations` auf `db/schema` + `db/seed`; `-placeholders.fdw_read_user` entfällt.
- [ ] `db/README.md`: Rollen `fdw_user`, `ts_flyway`, `core_flyway`, `core_app`, `pipeline_app` streichen, `monteis_ts`/`core`/`pipeline` dokumentieren, neue Rechtematrix.

## Phase A3 — Applikations-Config

- [ ] `core/application-dev.properties`: `META_DB_NAME` → `DB_NAME` (Port 5432 bleibt).
- [ ] `pipeline/application-dev.properties`: `localhost:5433/${TS_DB_NAME}` → `localhost:5432/${DB_NAME}`; User-Variablen angleichen.
- [ ] jOOQ-Codegen neu generieren: `SENSOR_READING_SECURED`/`RAW_SENSOR_READING` → `SENSOR_READING` in `MeasurementQueryRepository`, `OverviewQueryRepository`, `SensorDetailProjection`, CSV-Export-Repos. ⚠️ Spaltennamen prüfen (`sensor_id`→`das_key`, eingefrorene View-Spaltennamen).
- [ ] `pipeline`-Datasource zeigt auf dieselbe DB wie Core (nur anderer User); auf AWS keine Änderung nötig, da bereits TS-Instanz.
- [ ] **Pipeline jOOQ-Codegen** (`pipeline/pom.xml`):
  - Testcontainer: Init-Skript `docker/dataset-tsdb/init_tsdb.sql` → fusioniertes Init; DB-Name angleichen.
  - `flyway-maven-plugin`: `locations` `db/timescale/schema` → `db/schema`; Placeholder `fdw_read_user` entfernen.
  - `jooq-codegen-maven`: `<database><includes>sensor_reading|range_category</includes></database>` — sonst generiert die Pipeline Klassen für alle Meta-Tabellen mit.
- [ ] **Core jOOQ-Codegen** (`core/pom.xml`): heute zwei Container (`timescaledb` + `postgres:18-alpine`, Shared Network) und zwei Flyway-Executions (`db/timescale/schema` mit `fdw_read_user`, `db/meta/schema`) → ein Timescale-Container mit fusioniertem Init, eine Flyway-Execution auf `db/schema`.

## Phase A4 — Tests

- [ ] `core/.../TestcontainersConfiguration.java`: zwei Container → ein Timescale-Container mit fusioniertem Init; `fdw`-Placeholder entfernen.
- [ ] `pipeline/.../TestcontainersConfiguration.java`: analog.
- [ ] `RowLevelSecurityIT`, `RlsConnectionProviderTest`, `RowLevelSecurityTransactionTest`: `sensor_reading`-Cases ergänzen — `core` sieht nur eigenes Experiment, `pipeline` schreibt ohne GUCs, ungesetzte GUCs ⇒ leer.
- [ ] **Neuer Permissions-IT** (GRANT-Drift-Schutz): `core` hat kein INSERT auf `sensor_reading`, `pipeline` hat kein SELECT auf `experiments`, `core` kann keine Chunks direkt lesen — via `has_table_privilege`/Test-Query.
- [ ] **Pipeline-IT gegen RLS**: Upsert (`ON CONFLICT DO UPDATE`), `UPDATE … FROM (VALUES …)` und Reprocessing-Selects als `pipeline` mit aktivem RLS.
- [ ] `MeasurementQueryRepositoryIT`, Overview-IT: gegen lokale Tabelle, Spalten-Mapping prüfen.
- [ ] **Orphan-Monitoring**: periodische Query zählt `sensor_reading`-Zeilen mit `sensor_parameter_id` ohne passenden `sensor_parameter`; Alert bei Anstieg gegenüber Phase-B0-Referenz. Kann **nicht** über `core` laufen (RLS verfälscht das Ergebnis) → eigener Job mit `monteis_ts` oder dediziertem Monitoring-User mit `BYPASSRLS`/Owner-Rechten.
- [ ] **Performance-Vergleich** gegen Phase-A0-Baseline (erwartet: ≥ heute, Range-Scans deutlich besser) — **mit zwei Profilen**: `read_all_experiments = true` und eingeschränkter User mit wenigen Experimenten (kritisch: Overview `ORDER BY timestamp DESC LIMIT 100` scannt ggf. viele unsichtbare Zeilen rückwärts).

### A4a. Timescale-spezifische Prüfpunkte (lokal vor AWS verifizieren)

- [ ] **Compression/Columnstore + RLS**: Auf `ts2.28.1` testen (lokal mit `timescale/timescaledb-ha:pg18.4-ts2.28.1`), ob Compression auf Hypertables mit aktivem RLS erlaubt ist (ältere Versionen lehnten das ab). Relevant für geplante Retention/Compression (`timescale-retention.svg`).
- [ ] **Chunk-Direktzugriff**: Grants werden auf Chunks (`_timescaledb_internal._hyper_*`) kopiert; RLS-Policies der Hypertable greifen beim direkten Chunk-Select voraussichtlich nicht. Verifizieren, dass `core` keine Chunks direkt lesen kann. **In AWS zu prüfen**: `core`/`pipeline` sind Mitglied von `pg_read_all_data` → lesen jede Tabelle in jedem Schema inkl. Chunks; ein möglicher RLS-Bypass über direkten Chunk-Zugriff ist zu verifizieren. Wird in Phase B1 entfernt. In Permissions-IT aufnehmen.

## Phase A5 — Doku & Cleanup

- [ ] arc42 `05_building_block_view`, `06_runtime_view`, `09_architecture_decisions` (neue ADR „DB-Fusion, RLS auf Hypertable"), `08_concepts`, PUML-Diagramme (`overview.puml`, `erd-metadata.puml`).
- [ ] `.github/workflows/pr-gate.yml` und `docker/migration/Dockerfile` prüfen (FDW/TS-Flyway-Verdrahtung).
- [ ] `.trivyignore`, `renovate.json` ggf. anpassen.

## Abnahme-Gate A → B

- [ ] Frisches lokales Setup mit Init, Baseline und Seeds funktioniert reproduzierbar.
- [ ] Core und Pipeline laufen gegen dieselbe DB; jOOQ-Codegen, Tests und CI sind grün.
- [ ] RLS, explizite Grants, Pipeline-Upserts und direkter Chunk-Zugriff sind getestet; Compression-Kompatibilität ist geklärt oder Compression bleibt deaktiviert.
- [ ] Lokaler Performance-Vergleich ist abgeschlossen.
- [ ] **Deployment-Sperre:** Neue Anwendung und neue Baseline bis zum koordinierten AWS-Cutover nicht auf das alte AWS-Schema ausrollen. Release/Branch bzw. Deployment-Pipeline absichern; bisherige AWS-Migrations-Jobs bis dahin unverändert betreiben.

## Etappe B — Infra und AWS

Erst nach bestandenem Abnahme-Gate A werden Infra und AWS umgestellt. Die lokal geprüfte Baseline wird übernommen; AWS-Daten werden weiterhin per Dump/Restore migriert, ohne Rollback. RDS bleibt für Keycloak bestehen.

## Phase B0 — AWS-Vorbereitung

Die Angaben zu Versionen und Backups stammen aus dem Infra-Repo und ersetzen keine Prüfung des tatsächlich laufenden Clusters.
- [ ] **AWS-Datenstand sichern** (optional, nur Testdaten): `pg_dump` beider DBs als Debug-Referenz, falls der Cutover wiederholt werden muss.
- [ ] **Datenstand prüfen**: `sensor_reading`-Zeilen mit `sensor_parameter_id IS NULL` zählen — sie werden nach der Fusion für `core` unsichtbar (gleiches Verhalten wie heute im View). Verwaiste IDs zählen — als Superuser/Owner in der Meta-DB über die noch bestehende FDW (`raw_sensor_reading LEFT JOIN sensor_parameter`; nicht als `core`, sonst verfälscht die RLS auf `sensor_parameter` das Ergebnis). Ergebnis dient als Referenzwert für das Orphan-Monitoring (Phase A4).
- [ ] **Cutover-Fenster planen**: **Core** gestoppt ab Meta-Dump bis zur Umstellung der Datasource. **Pipeline** schreibt bereits heute in die TS-Instanz und muss nur während des Cutover-Skripts (B2) pausiert werden (`ENABLE ROW LEVEL SECURITY` nimmt einen exklusiven Lock; zudem verliert `pipeline` mit dem Entfernen von `pg_write_all_data` (B1) kurz die Rechte, bis die expliziten Grants greifen); Kafka puffert.
- [x] **PG-Version**: RDS `18.4` (alle tfvars) = CNPG `pg18.4` → Restore kompatibel.
- [x] **Extensions**: Meta-DB braucht nur `postgres_fdw` (entfällt); `timescaledb` wird von CNPG via `postInitApplicationSQL` angelegt.
- [x] **Backup/PITR**: CNPG Barman Cloud Plugin, WAL-Archivierung nach S3, Retention 30 Tage (`tsdb_backup_retention_days`).
- [ ] **Collation/Encoding**: `\l` auf RDS (`monteis`) und CNPG (`monteis_ts`) vergleichen.
- [ ] **Sizing**: dev-Cluster 1 Instanz, 0.5–1 CPU, 1–2 GiB RAM, 30 GiB gp3 — mit zusätzlicher Meta-Last prüfen; staging/prod-Overlays separat.

## Phase B1 — Infra-Änderungen vorbereiten (`lg-monteis-infra`)

Änderungen zunächst vorbereiten, erst koordiniert mit Phase B3 ausrollen. Bestehende Deployments bis dahin unverändert lassen.

- [ ] `kubernetes/timescaledb/base/cluster.yaml`: `inRoles: pg_read_all_data` bei `core` und `pg_write_all_data`/`pg_read_all_data` bei `pipeline` **entfernen** (sonst möglicher RLS-Bypass via Chunks + Pipeline-Schreibrecht auf alle Meta-Tabellen). Achtung: ab diesem Moment braucht `pipeline` die expliziten Grants auf `sensor_reading` → zusammen mit B2 ausrollen.
- [ ] `cluster.yaml`: `fdw_user` erst nach `DROP OWNED` in B2 auf `ensure: absent` (Löschreihenfolge mit CNPG koordinieren); `externalsecret-fdw` entfernen; `aws_secretsmanager_secret.fdw` + Eintrag in `sm_reader`-Policy/`secret_arns` in `modules/timescaledb` entfernen.
- [ ] Core-Deployment: Datasource auf `timescaledb-rw`/`monteis_ts`, User `core` aus Secret `timescaledb-core` (statt `core/rds-credentials`).
- [ ] Meta-Migration-Job: statt `core_flyway` (`migration/rds-credentials`) mit `monteis_ts` (Secret `timescaledb-app`) gegen `monteis_ts` — ein gemeinsamer Flyway-Job für `db/schema`.
- [ ] Nach Cutover: RDS-DB `monteis` droppen; SM-Secrets `core/rds-credentials`, `migration/rds-credentials` + zugehörige SM-Reader-Rollen in `modules/sm-eso` entfernen; `modules/db-access` (`secret_arns`) und `kubernetes/db-relay` (Ziel `monteis`) anpassen. RDS-Instanz bleibt für Keycloak.
- [ ] AWS: siehe Phase B1 (`lg-monteis-infra`). `application-prod.properties` bleibt unverändert (`CORE_META_DB_JDBC_URL`, `CORE_APP_META_DB_USER/PWD`), nur die Werte im Deployment ändern.

## Phase B2 — AWS-Cutover-Skript vorbereiten

- **AWS**: Bestehender Zustand wird per einmaligem Cutover-Skript (B2) auf den Baseline-Zustand gebracht, danach `flyway baseline -baselineVersion=1`. Künftige Änderungen ab `V2__…`.

Läuft als Admin auf der TS-Instanz **nach** dem Meta-Restore (Phase B3) und bringt den Bestand auf exakt den Baseline-Zustand. Ablage z.B. `db/cutover/merge_meta_into_tsdb.sql`.

```sql
BEGIN;

-- 1. Absicherung: FDW-Objekte werden bereits beim Restore herausgefiltert (Phase B3)
DROP VIEW IF EXISTS sensor_reading_secured;
DROP FOREIGN TABLE IF EXISTS raw_sensor_reading;
DROP SERVER IF EXISTS timescale_server CASCADE;
DROP EXTENSION IF EXISTS postgres_fdw;

-- 2. Alte TS-Flyway-History entfernen — `flyway baseline` bricht sonst ab,
--    weil bereits eine nicht-leere flyway_schema_history existiert
DROP TABLE flyway_schema_history;

-- 3. Altrolle fdw_user entrechten (sensor_reading gehört in AWS bereits monteis_ts)
DROP OWNED BY fdw_user;                     -- SELECT auf sensor_reading (TS-V1)

-- 4. Meta-Grants für core neu vergeben (Restore läuft mit --no-acl, siehe Phase B3)
GRANT USAGE ON SCHEMA public TO core, pipeline;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO core;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO core;
ALTER DEFAULT PRIVILEGES FOR ROLE monteis_ts IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core;
ALTER DEFAULT PRIVILEGES FOR ROLE monteis_ts IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO core;

-- 5. RLS-Policies + sensor_reading-Grants: identisch zu Baseline-Abschnitt 2 + 3
--    (REVOKE INSERT/UPDATE/DELETE ON sensor_reading FROM core korrigiert Schritt 4)

COMMIT;
```

`fdw_user` wird **nicht** per `DROP ROLE` entfernt — CNPG würde die Rolle wieder anlegen (`ensure: present`); Abbau über das Infra-Repo (Phase B1).

Danach Flyway-History neu aufsetzen:

```bash
flyway -locations=filesystem:db/schema baseline -baselineVersion=1 -baselineDescription="merged baseline"
```

- [ ] **Äquivalenz-Check (Pflicht)**: Schema einer frischen Baseline-Installation vs. Schema nach Cutover-Skript vergleichen (`pg_dump --schema-only --exclude-table=flyway_schema_history` beider DBs + `diff`, oder `migra`). Muss leer sein (bis auf Timescale-Chunk-Interna).
- [ ] Cutover zuerst auf der ersten AWS-Umgebung (z.B. dev) durchspielen, danach auf den weiteren.

## Phase B3 — AWS-Cutover durchführen

1. Rollen `monteis_ts`, `core`, `pipeline` existieren bereits (CNPG). `core_flyway`/`core_app` existieren nur auf RDS und werden nicht übernommen (`pg_dump` überträgt keine Rollen).
2. Core stoppen.
3. `pg_dump -Fc --exclude-table=flyway_schema_history` der Meta-DB (RDS).
4. Restore-Liste filtern: `pg_restore -l dump > list`, daraus entfernen: `EXTENSION postgres_fdw` (+ `COMMENT`), `SERVER timescale_server`, `USER MAPPING`, `FOREIGN TABLE raw_sensor_reading`, `VIEW sensor_reading_secured` — `CREATE EXTENSION postgres_fdw` würde als `monteis_ts` mangels Superuser-Recht scheitern.
5. `pg_restore --no-owner --no-acl --role=monteis_ts -L list` in `monteis_ts` (alle Meta-Objekte gehören danach `monteis_ts`). `--no-acl`, weil die Grants im Dump auf `core_app`/`core_flyway` lauten, die es hier nicht gibt; Grants für `core` vergibt B2.
6. Pipeline pausieren → Cutover-Skript B2 + Infra-Änderung `inRoles` (B1) ausrollen → Pipeline fortsetzen.
7. `flyway baseline -baselineVersion=1`.
8. Äquivalenz-Check (B2) gegen frische Baseline.
9. Core-Datasource von RDS auf die TS-DB umstellen, Core starten. Pipeline-Datasource bleibt unverändert (zeigt bereits auf die TS-Instanz).

## Phase B4 — AWS-Abnahme und Cleanup

- [ ] Anwendung und Ingest prüfen; Permissions- und RLS-Checks mit den tatsächlichen AWS-Rollen wiederholen.
- [ ] Effektive Rollenmitgliedschaften und direkten Chunk-Zugriff nach CNPG-Reconciliation prüfen.
- [ ] Erst nach erfolgreicher Abnahme alte RDS-Meta-DB und nicht mehr benötigte Secrets/Verweise entfernen; Keycloak und dessen RDS-Zugriff erhalten.
- [ ] Vor Entfernen gemeinsamer IAM-Rollen alle Verbraucher prüfen; insbesondere Fulcrum-Token-Berechtigungen der `core-sm-reader`-Rolle erhalten.
- [ ] Betriebsdokumentation und Monitoring aktualisieren; weitere AWS-Umgebungen erst nach erfolgreichem Durchlauf auf AWS-dev umstellen.

## Grösste Risiken

- **GRANT-Drift im Ein-Schema-Modell**: Default-Privileges gelten pro Ersteller-Rolle; ein vergessener `REVOKE` gibt `core` still Schreibrecht → Permissions-IT (Phase A4) als Regressionstest.
- **NULL/verwaiste `sensor_parameter_id`** (kein FK) → Messwerte still unsichtbar wie heute; Phase-B0-Referenzwert + Orphan-Monitoring (Phase A4).
- **Baseline weicht vom AWS-Bestand ab** (vergessene Policy/Grant/Funktion beim Squash) → frische Setups und AWS divergieren still → Äquivalenz-Check (Phase B2) als Pflicht-Gate, idealerweise als CI-Job wiederholbar.
- **Bestehende TS-History blockiert `flyway baseline`** → Meta-History vom Dump ausschliessen (Phase B3), TS-History im Cutover-Skript droppen (B2).
- **Restore scheitert an FDW-Objekten** (`CREATE EXTENSION` braucht Superuser) → Restore-Liste filtern (Phase B3, Schritt 4).
- **Schreibzugriffe während Meta-Dump/Restore** → Core gestoppt; Pipeline nur während B2 pausiert.
- **Workload-Isolation entfällt**: Ingest- und Lese-Last teilen sich Instanz-Ressourcen; unter hoher Datenrate beobachten.
- **Meta-DB verliert RDS-Features** (Multi-AZ, Performance Insights, Enhanced Monitoring, Auto-Minor-Upgrade) → CNPG bietet Backup/PITR via Barman; HA erst mit `instances > 1` (staging/prod-Overlays prüfen), Monitoring separat.
- **`pg_read_all_data`/`pg_write_all_data` in AWS** → RLS-Bypass via Chunks und Pipeline-Schreibrecht auf Meta, falls B1 vergessen wird → Permissions-IT reicht hier nicht (lokal anders konfiguriert); Check nach Cutover auf AWS ausführen.

## Geschätzter Umfang

1 Baseline-Skript + 1 Cutover-Skript, ~10 Config-/Infra-Dateien, ~6 Testdateien, jOOQ-Umbau in 3–4 Repositories.
