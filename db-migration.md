# Migrationsplan: Fusion Meta-DB + TimescaleDB

Die Meta-DB und die TimescaleDB werden zu **einer** Datenbank mit **einem** Schema `public` zusammengeführt; Die FDW-Extension entfällt, weil dies der Engpass beim Lesen der Zeitreihen ist. Dieser Plan deckt nur den App-Teil (`lg-monteis`) ab. Jede Umgebung startet mit einer leeren Datenbank; übernommen wird nur das Flyway-DDL. In Testcontainers spielt Flyway zusätzlich die Seed-Daten aus `db/seed` ein.

Referenz (im Workspace, nicht wiederholt): welche Dateien, Rollen und Variablen an der Zwei-DB-Topologie hängen, steht in der [Database Dependency Map](../aidlc/spaces/default/codekb/lg-monteis/dependencies.md#database-dependency-map-input-for-the-planned-db-merge); Schema, Rollen und RLS-Kontext heute in [architecture.md](../aidlc/spaces/default/codekb/lg-monteis/architecture.md#data-layer-current-state).

## Änderungen gegenüber Vorversion

**Entfernt**

- Jede Datenübernahme aus AWS: Dump/Restore der Meta-DB (`pg_dump`, gefilterte `pg_restore`-Liste), Cutover-Skript `db/cutover/merge_meta_into_tsdb.sql`, Orphan-Bereinigung vor der FK-Anlage, `flyway baseline` auf einem bestehenden Schema, Äquivalenz-Check Cutover gegen frische Baseline.
- Phase A0 (Orphan-Zählwerte) und das Zuordnungs-Monitoring gegen A0/B0. Die Vorher-Messung der Performance steht jetzt im Abschnitt Tests.
- Etappe B (Infra-Aufgaben, AWS-Vorbereitung, Cutover-Fenster, Abbau alter Secrets). Ersetzt durch „Umstellung AWS" und einen kurzen „Hinweis Infrastruktur".
- Prüfpunkt Compression/Columnstore mit RLS (nicht Teil dieser Arbeit) und der Abschnitt „Geschätzter Umfang".

**Korrigiert gegen den Code**

- Die CSV-Export-Repositories (`JooqExperimentCsvExportQueryRepository`, `JooqSensorCsvExportQueryRepository`, `JooqOrganisationCsvExportQueryRepository`) lesen `sensor_reading` nicht und sind nicht betroffen; die Vorversion führte sie in A3 auf.
- Der `forcedType` auf `sensors.unit` in `core/pom.xml` ist seit Meta V9 veraltet (die Spalte `unit` liegt in `sensor_parameter`).
- Der Pipeline-Codegen in `pipeline/pom.xml` hat heute keine `<includes>`; nach der Fusion würde er alle Meta-Tabellen mitgenerieren.
- Meta V6, V9, V11 und V14 vergeben `SELECT` auf `sensor_reading_secured` hart codiert an `core_app`. Die Baseline vergibt Rechte nur an `core` und `pipeline`.
- Gelöschte Sensor-Parameter werden heute nicht über Kafka gemeldet, und `SensorConfigMessageHandler` verwirft Tombstones, statt den Cache zu bereinigen.
- Ein einzelner fehlerhafter Datensatz lässt heute den ganzen Batch scheitern (`SensorDataBatchProcessor`, ein Upsert pro Batch) und schickt nach zwei Retries alle Datensätze in `internal-normalized-dlt`.
- `.env.example.local` liegt unter `docker/`, die Test-Properties unter `src/main/resources`.

**Neu**

- Vollständiger App-Fix für gelöschte Parameter (A5): Tombstone nach Commit, Cache-Eviction; TBD: einmaliger NULL-Retry bei SQLSTATE `23503`.
- Kafka-Reset bei der Umstellung: `internal-sensor-config` wird neu angelegt, die Offsets auf `internal-normalized` werden auf `latest` gesetzt.
- Rechte auf die JaVers-Tabellen `jv_*` stehen explizit in der Rechtematrix und im Permissions-IT.
- FDW-Kommentare in `QueryTimingListener` und der Leak-Test „through the secured view" in `MeasurementQueryRepositoryIT` werden angepasst.
- Die Seed-Daten bleiben für lokale Tests: `db/meta/seed` und `db/timescale/seed` werden zu einer Location `db/seed` zusammengeführt, die nur lokal und in Testcontainers läuft, nie in AWS.
- Lokal bleibt die TimescaleDB (`ts_db`, `flyway-timescale`) und nimmt die Meta-Tabellen auf; `metadata_db` und `flyway-metadata` entfallen (A2.2).

**Offen (TBD)**

- TBD: Die Funktion `missing_sensor_parameter_ids` und der NULL-Retry, der auf ihr aufbaut (Baseline-SQL Schritt 4, Spalte in der Rechtematrix, A1.1, A4.2, A5.3, A5.4, T2), sind noch nicht entschieden. Alles, was an ihr hängt, ist mit TBD markiert und wird erst nach dieser Entscheidung umgesetzt oder gestrichen.
- TBD: Vom NULL-Retry hängen ausserdem ab: der Retry-Teil von „Vollständiger App-Fix" (oben unter „Neu"), der Satz zu `23503` im Zielbild, der Hinweis zu älteren DAS-Keys unter A5, der Race-Teil von T4 und damit die Abnahme-Zeile zu T1–T6.

## Zielbild

- Eine PostgreSQL-18-Datenbank `monteis` mit TimescaleDB, Owner `monteis_ts`, ein einziges Schema `public`. Der Name gilt in jeder Umgebung (lokal, Testcontainers, CI, AWS). Weil `public` seit PostgreSQL 15 der Rolle `pg_database_owner` gehört, ist damit auch `monteis_ts` Owner von `public`. Lokal und in Tests das Image `timescale/timescaledb:latest-pg18`.
- Keine FDW-Objekte: weder Extension `postgres_fdw` noch Server `timescale_server`, User Mapping oder Foreign Table `raw_sensor_reading`.
- Kein View `sensor_reading_secured`.
- `sensor_reading` ist eine lokale Hypertable mit nativer RLS (`ENABLE ROW LEVEL SECURITY`, zwei Policies, siehe SQL unten).
- FK `sensor_reading.sensor_parameter_id → sensor_parameter.id ON DELETE SET NULL`. Wird ein Parameter gelöscht, bleiben seine Messwerte erhalten, ihre Zuordnung wird `NULL`, und `core` sieht sie nicht mehr. `NULL` ist erlaubt; eine unbekannte Nicht-NULL-ID weist die Datenbank mit `23503` ab. TBD: Ob die Pipeline darauf mit einem einmaligen NULL-Retry reagiert (A5.3, A5.4), ist noch nicht entschieden.
- Jede Umgebung (lokal, Testcontainers, CI, AWS) startet leer. Flyway läuft als `monteis_ts` und wendet `db/schema/V1__baseline.sql` an; spätere Änderungen folgen ab `V2__…`. In AWS ist `db/schema` die einzige Location, die Datenbank bleibt ohne Daten. Lokal (geseedete Make-Ziele, Compose) und in Testcontainers (Core-ITs, E2E) kommt die repeatable Location `db/seed` dazu. Flyway bleibt ausserhalb der Apps (Migration-Image, Jobs, Tests und Codegen programmatisch).
- Backfill: Das Reprocessing ordnet Messwerte mit `sensor_parameter_id = NULL` dem aktuellen Parameter mit demselben DAS-Key zu. Das gilt auch für Messwerte, deren Parameter gelöscht wurde: Wird unter demselben DAS-Key wieder ein Parameter angelegt, erhält er diese Messwerte.

### Erstellung `db/schema/V1__baseline.sql`

- Meta-Teil aus einem Schema-only-Export eines frischen Setups auf Stand Meta V23 / TimescaleDB V4. Entfernt werden: Extension `postgres_fdw`, Server `timescale_server`, User Mapping, Foreign Table `raw_sensor_reading`, View `sensor_reading_secured`, Owner-Angaben und alle Grants an alte Rollen.
- Erhalten bleiben: RLS-Funktionen (`can_access_experiment`, `can_access_sensor`, `can_write_experiment`, `can_write_an_experiment_of_sensor`), alle Policies, die JaVers-Tabellen und -Sequenzen aus Meta V4, die Enums `unit` und `range_category`, alle Indizes und die weiterhin gültigen Kommentare (Index-Begründungen aus TS V1/V3, RLS-Begründungen aus Meta V6/V10).
- Zeitreihen-Teil von Hand, nach `sensor_parameter` (nicht aus dem Export, der Timescale-Interna enthält):

```sql
-- 1. Enum + Tabelle (Spiegel von TS V1..V4, ergänzt um FK auf sensor_parameter)
CREATE TYPE range_category AS ENUM ('too_low', 'correct', 'too_high');

CREATE TABLE sensor_reading (
    "timestamp"          TIMESTAMPTZ NOT NULL,
    das_key              VARCHAR(500) NOT NULL,
    raw_value            DOUBLE PRECISION NOT NULL,
    norm_value           DOUBLE PRECISION,
    version              SMALLINT DEFAULT 0,
    status               range_category,
    sensor_parameter_id  UUID,
    PRIMARY KEY ("timestamp", das_key),
    CONSTRAINT sensor_reading_sensor_parameter_fk
        FOREIGN KEY (sensor_parameter_id) REFERENCES sensor_parameter(id)
        ON DELETE SET NULL
);

CREATE INDEX sensor_reading_das_key_time_idx
    ON sensor_reading (das_key, "timestamp" DESC);
CREATE INDEX sensor_reading_sensor_parameter_time_idx
    ON sensor_reading (sensor_parameter_id, "timestamp" DESC);

SELECT create_hypertable('sensor_reading', 'timestamp', create_default_indexes => FALSE);

-- 2. RLS (ersetzt sensor_reading_secured)
-- core: gleiche Semantik wie der heutige View-Join (V14). Der Subselect auf
-- sensor_parameter läuft als core; dessen RLS (V10, can_access_sensor) filtert
-- transitiv. NULL-Zuordnungen matchen nie und bleiben unsichtbar.
ALTER TABLE sensor_reading ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensor_reading_read ON sensor_reading FOR SELECT TO core
    USING (sensor_parameter_id IN (SELECT id FROM sensor_parameter));

-- pipeline: FOR ALL, weil Upsert (ON CONFLICT DO UPDATE), UPDATE ... FROM (VALUES ...)
-- und die Reprocessing-Selects in SensorReadingRepository auch SELECT-Policies brauchen.
CREATE POLICY sensor_reading_pipeline ON sensor_reading FOR ALL TO pipeline
    USING (true) WITH CHECK (true);

-- 3. Grants: die Default Privileges von monteis_ts geben core CRUD auf jede neue Tabelle;
-- auf sensor_reading erst alles entziehen, dann nur SELECT vergeben.
REVOKE ALL ON sensor_reading FROM core;
GRANT SELECT ON sensor_reading TO core;
GRANT SELECT, INSERT, UPDATE, DELETE ON sensor_reading TO pipeline;
-- Die Chunks erben diese Grants, aber nicht die RLS. Ohne USAGE auf das Chunk-Schema
-- scheitert jeder direkte Chunk-Select; Lesen und Schreiben über sensor_reading
-- (inkl. RLS) bleiben unberührt (Prüfung in T2).
REVOKE USAGE ON SCHEMA _timescaledb_internal FROM PUBLIC, core, pipeline;

-- 4. TBD (Entscheidung offen, siehe „Änderungen gegenüber Vorversion"):
-- Lookup für den NULL-Retry der Pipeline (A5.3), ohne Leserecht auf Meta-Tabellen.
-- Läuft als Owner monteis_ts; RLS auf sensor_parameter ist nicht FORCE, filtert also nicht.
CREATE FUNCTION missing_sensor_parameter_ids(ids UUID[])
    RETURNS SETOF UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT t.candidate
    FROM unnest(ids) AS t(candidate)
    WHERE NOT EXISTS (SELECT 1 FROM sensor_parameter p WHERE p.id = t.candidate)
$$;
REVOKE ALL ON FUNCTION missing_sensor_parameter_ids(UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION missing_sensor_parameter_ids(UUID[]) TO pipeline;
```

- Alte Migrationen ziehen nach `db/archive/meta` und `db/archive/timescale` um und liegen damit ausserhalb jeder Flyway-Location.
- Seeds: `db/meta/seed/R__seed_dev_data.sql` und `db/timescale/seed/R__seed_dev_data.sql` werden zu `db/seed/R__seed_dev_data.sql` zusammengeführt, zuerst der Meta-Teil, danach `sensor_reading`, weil der FK die `sensor_parameter`-Zeilen voraussetzt. Das `TRUNCATE … CASCADE` des Meta-Teils leert über den FK jetzt auch `sensor_reading`. Der Kommentar, die IDs zwischen zwei Datenbanken von Hand synchron zu halten, entfällt.

## Rollen und Rechte

Die DB-Rollen sind technische Benutzer, keine fachlichen Identitäten. Welche Experimente und Messwerte ein angemeldeter Benutzer sieht, entscheidet RLS anhand des Kontexts, den `RlsConnectionProvider` aus den Keycloak-Gruppen setzt (siehe [architecture.md, Row-level security context](../aidlc/spaces/default/codekb/lg-monteis/architecture.md#row-level-security-context)).

|    Rolle     |                                      Meta-Tabellen                                      |                 JaVers `jv_*` (4 Tabellen, 3 Sequenzen)                  |                         `sensor_reading`                         | Chunks `_timescaledb_internal._hyper_*` | Funktion `missing_sensor_parameter_ids` (TBD) |
|--------------|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------|------------------------------------------------------------------|-----------------------------------------|-----------------------------------------------|
| `monteis_ts` | Owner, DDL (Flyway)                                                                     | Owner                                                                    | Owner, DDL                                                       | Owner (über TimescaleDB)                | Owner (TBD)                                   |
| `core`       | SELECT, INSERT, UPDATE, DELETE; `USAGE, SELECT` auf Sequenzen; RLS je Tabelle wie heute | SELECT, INSERT, UPDATE, DELETE; `USAGE, SELECT` auf Sequenzen; keine RLS | nur SELECT, gefiltert durch `sensor_reading_read`                | kein direkter Zugriff                   | kein Recht (TBD)                              |
| `pipeline`   | kein Zugriff                                                                            | kein Zugriff                                                             | SELECT, INSERT, UPDATE, DELETE, Policy `sensor_reading_pipeline` | kein direkter Zugriff                   | EXECUTE (TBD)                                 |

- `core` und `pipeline` erhalten `USAGE` auf `public`. Keine Rolle ist Mitglied von `pg_read_all_data` oder `pg_write_all_data`; es gelten nur explizite Grants.
- Die Rechte von `core` auf Meta- und `jv_*`-Tabellen kommen aus `ALTER DEFAULT PRIVILEGES FOR ROLE monteis_ts IN SCHEMA public`: `SELECT, INSERT, UPDATE, DELETE` auf Tabellen und `USAGE, SELECT` auf Sequenzen, jeweils an `core`. Deshalb entzieht die Baseline `core` auf `sensor_reading` alle Rechte (`REVOKE ALL`) und vergibt danach nur `SELECT`.
- `pipeline` erhält keine Default Privileges, nur die expliziten Grants auf `sensor_reading` aus der Baseline; sonst bekäme die Pipeline CRUD auf alle Meta-Tabellen.
- Die Policy `sensor_reading_pipeline` filtert nicht, wirkt aber nur innerhalb dieser Grants.
- TBD: Einzige Ausnahme für `pipeline` ausserhalb von `sensor_reading` wäre `EXECUTE` auf `missing_sensor_parameter_ids(UUID[])`. Die Funktion liefert nur, welche der übergebenen IDs nicht existieren, und gibt keine Meta-Daten preis. Ob es sie gibt, ist noch nicht entschieden.
- Chunks: TimescaleDB überträgt die Grants der Hypertable auf die Chunks, also `SELECT` an `core` und `SELECT, INSERT, UPDATE, DELETE` an `pipeline`; die RLS-Policies greifen beim direkten Chunk-Select aber nicht. Deshalb entzieht die Baseline `PUBLIC`, `core` und `pipeline` das Recht `USAGE` auf das Schema `_timescaledb_internal` (Schritt 3); die RLS sitzt auf `sensor_reading` und ist davon nicht betroffen. Der Permissions-IT T2 prüft, dass ein direkter Chunk-Select für beide Rollen scheitert, `core` über `sensor_reading` weiter liest (gefiltert durch RLS) und `pipeline` über `sensor_reading` weiter schreibt. Brechen Lesen oder Schreiben über die Hypertable, fällt der Entzug aus `V1__baseline.sql` wieder heraus, und der direkte Chunk-Zugriff wird als Risiko akzeptiert (siehe Risiken).

## Phasen

Jede Aufgabe ist ein Ticket. Pfade relativ zu `lg-monteis/`; `core/.../` steht für `core/src/main/java/ch/swisstopo/monteis/core/`, `pipeline/.../` für `pipeline/src/main/java/ch/swisstopo/monteis/pipeline/`. Neue Variablennamen: `DB_NAME` (Wert `monteis`), `DB_FLYWAY_USER/PWD` (Rolle `monteis_ts`), `CORE_DB_USER/PWD` (`core`), `PIPELINE_DB_USER/PWD` (`pipeline`), `CORE_DB_JDBC_URL` (prod).

### A1 — Baseline

- [ ] **A1.1** `db/schema/V1__baseline.sql` — Baseline wie unter „Erstellung" beschrieben schreiben; die Funktion `missing_sensor_parameter_ids` (Schritt 4) ist TBD und kommt nur nach positiver Entscheidung hinein. *Fertig:* Flyway wendet sie als `monteis_ts` fehlerfrei auf eine leere Datenbank an; das Ergebnis enthält kein FDW-Objekt und keinen View `sensor_reading_secured`; ein Objektvergleich (Funktionen, Policies, Indizes, Enums) mit einem frischen Setup auf heutigem Stand zeigt nur die gewollten Unterschiede.
- [ ] **A1.2** `db/archive/meta`, `db/archive/timescale`, `db/seed` — Meta V1–V23 und TS V1–V4 nach `db/archive` verschieben; die Seeds aus `db/meta/seed` und `db/timescale/seed` wie unter „Erstellung" beschrieben nach `db/seed` verschieben und zusammenführen, statt sie zu löschen. Dabei werden die FDW-Kommentare des Timescale-Seeds umgeschrieben: die `sensor_parameter_id`-Werte müssen zu den `sensor_parameter`-Zeilen weiter oben in derselben Datei passen; `ANALYZE sensor_reading` bleibt, weil eine Hypertable ohne Statistik schlecht geplant wird. Das separate `TRUNCATE TABLE sensor_reading;` bleibt. *Fertig:* `db/schema` enthält nur `V1__baseline.sql`, `db/seed` nur `R__seed_dev_data.sql`; `db/seed` enthält kein FDW und kein `postgres_fdw`; keine Flyway-Location zeigt auf `db/archive`.

### A2 — Lokales Setup

- [ ] **A2.1** `docker/dataset-init/init.sql` (ersetzt `docker/dataset-meta/init_meta.sql` und `docker/dataset-tsdb/init_tsdb.sql`) — `CREATE EXTENSION timescaledb`; Rollen `monteis_ts`, `core`, `pipeline` (lokale Dev-Passwörter wie heute); die vom Image über `POSTGRES_DB=monteis` angelegte Datenbank geht mit `ALTER DATABASE monteis OWNER TO monteis_ts` an `monteis_ts` und damit auch `public`; `USAGE` für `core` und `pipeline`; Default Privileges von `monteis_ts` an `core`: `SELECT, INSERT, UPDATE, DELETE` auf Tabellen, `USAGE, SELECT` auf Sequenzen; nichts davon für `pipeline`; keine FDW-Grants, kein Server, kein User Mapping. *Fertig:* beide alten Init-Ordner sind gelöscht; `\l monteis` und `\dn` zeigen `monteis_ts` als Owner der Datenbank und von `public`, `\dn` kein weiteres Schema ausser denen von TimescaleDB; `\ddp` zeigt keine Default Privileges für `pipeline`.
- [ ] **A2.2** `docker/compose.yml` — die Meta-Tabellen ziehen in die TimescaleDB: `ts_db` und `flyway-timescale` bleiben mit `profiles: [ core, pipeline ]`, `metadata_db` und `flyway-metadata` werden entfernt. `ts_db` initialisiert mit `docker/dataset-init` statt `docker/dataset-tsdb`, lauscht auf Port 5432 (heute 5433, A3 setzt das voraus) und nimmt `DB_NAME`; `flyway-timescale` mountet `db/schema` und `db/seed` statt `db/timescale/*`, läuft mit `DB_FLYWAY_USER/PWD`, ohne `-placeholders.fdw_read_user`, und nimmt die Locations aus `FLYWAY_LOCATIONS` (Standard: beide); ein eigenes Flyway-Schema für die Pipeline gibt es nicht. *Fertig:* `docker compose --profile core --profile pipeline up` startet auf leerem Volume genau eine Datenbank und einen Flyway-Lauf, mit Seed-Daten; mit `FLYWAY_LOCATIONS` nur auf `db/schema` ohne.
- [ ] **A2.3** `docker/Makefile` — alle Ziele behalten ihre Namen (`core`, `pipeline-empty`, `pipeline-seeded`, `all-empty`, `all-seeded`, `down`, `logs`); nur `TS_FLYWAY_LOCATIONS` wird zu `FLYWAY_LOCATIONS`. *Fertig:* kein `TS_`/`META_`/`FDW_` mehr im Makefile; `make all-seeded` startet die eine Datenbank mit Seed-Daten, `make all-empty` ohne; `make pipeline-seeded`, `make pipeline-empty` und `make core` starten jeweils dieselbe eine Datenbank.
- [ ] **A2.4** `docker/.env.example.local` — auf die neuen Variablennamen umstellen; `TS_*`, `META_*`, `FDW_*`, `CORE_APP_META_DB_*`, `PIPELINE_APP_TS_DB_*` entfernen. *Fertig:* die Datei enthält keines dieser Präfixe mehr.
- [ ] **A2.5** `db/README.md` — Rollen `fdw_user`, `ts_flyway`, `core_flyway`, `core_app`, `pipeline_app` streichen; `monteis_ts`, `core`, `pipeline` und die Rechtematrix dieses Plans dokumentieren; Hinweis „Datenbank startet leer, `db/seed` nur lokal und in Tests". *Fertig:* README beschreibt nur die neue Struktur `db/schema` + `db/seed` (nur lokal und in Tests) + `db/archive`.

### A3 — Applikations-Konfiguration

- [ ] **A3.1** `core/src/main/resources/application-dev.properties`, `application-prod.properties` — Datasource auf `${DB_NAME}` bzw. `${CORE_DB_JDBC_URL}` und `CORE_DB_USER/PWD`. *Fertig:* kein `META_` mehr in beiden Dateien; Core startet lokal gegen die eine Datenbank.
- [ ] **A3.2** `pipeline/src/main/resources/application-dev.properties` — `localhost:5433/${TS_DB_NAME}` → `localhost:5432/${DB_NAME}`, `PIPELINE_DB_USER/PWD`. *Fertig:* Pipeline schreibt lokal in dieselbe Datenbank wie Core, als Rolle `pipeline`.

### A4 — jOOQ-Codegen und Lesepfade

- [ ] **A4.1** `core/pom.xml` — ein TimescaleDB-Container mit `docker/dataset-init/init.sql` statt zwei Containern auf `Network.SHARED`; eine Flyway-Execution auf `db/schema` (Execution `flyway-migrate-timescale` und Placeholder `fdw_read_user` entfallen); `forcedType` von `sensors.unit` auf `sensor_parameter.unit` korrigieren. *Fertig:* Codegen erzeugt `SensorReading` und `RangeCategory`, aber weder `SensorReadingSecured` noch `RawSensorReading`, und `SENSOR_PARAMETER.UNIT` hat den Typ `Unit`.
- [ ] **A4.2** `pipeline/pom.xml` — Testcontainer mit dem neuen Init, Flyway auf `db/schema` ohne Placeholder, `<includes>sensor_reading|range_category</includes>` im jOOQ-Codegen. *Fertig:* die Pipeline generiert nur diese beiden Objekte; `missing_sensor_parameter_ids` (TBD) ruft A5.3 per Plain-SQL auf und wird nicht generiert.
- [ ] **A4.3** `core/.../modules/measurement/jooq/MeasurementQueryRepository.java` — `SENSOR_READING_SECURED` → `SENSOR_READING`; `DSL.inline(...)` für `from`/`to`/IDs durch Bind-Werte ersetzen; `status` als `RangeCategory` statt `String` lesen; die beiden Kommentare, die auf `db/meta/schema/V14` bzw. `db/meta/schema/V13` zeigen (heute Zeilen 60 und 156), verweisen auf `V1__baseline.sql`. *Fertig:* kein Import von `SensorReadingSecured`, kein `DSL.inline` mehr für Pushdown, kein Bezug auf `db/meta/` in der Datei.
- [ ] **A4.4** `core/.../modules/measurement/jooq/SensorDetailProjection.java` — Felder aus `SENSOR_READING`, `status` als Enum. *Fertig:* kein `String.class` mehr für `status`.
- [ ] **A4.5** `core/.../modules/overview/jooq/OverviewQueryRepository.java` — liest `SENSOR_READING`; Kommentar zum lokalen LIMIT und `fetch_size` an die neue Lage anpassen. *Fertig:* kein Bezug auf den View oder FDW mehr in der Klasse.
- [ ] **A4.6** `core/src/test/java/ch/swisstopo/monteis/core/modules/measurement/jooq/MeasurementQueryRepositoryIT.java` — liest `SENSOR_READING`; der Test `should_not_leak_readings_of_invisible_sensors_through_the_secured_view` wird in `..._through_row_level_security` umbenannt und bleibt erhalten. *Fertig:* IT grün, kein Bezug auf den View.

### A5 — Gelöschte Sensor-Parameter

- [ ] **A5.1** `core/.../modules/sensor/jooq/JooqSensorRepository.java`, `core/.../modules/sensor/service/SensorService.java`, `core/.../infrastructure/kafka/SensorConfigPublisher.java` — `update` liefert die entfernten Parameter; `SensorService` berechnet für jeden den DAS-Key `<das>__<das_sensor_alias>__<das_parameter_alias>` aus den Zeilen vor dem Löschen und sendet nach dem Commit (`TransactionSynchronization.afterCommit`) einen Tombstone (Key = DAS-Key, Value = `null`) auf `internal-sensor-config`. *Fertig:* Test zeigt genau einen Tombstone pro entferntem Parameter nach Commit und keinen bei Rollback.
- [ ] **A5.2** `pipeline/.../transformation/processing/cache/SensorConfigCacheHydrationListener.java`, `SensorConfigCache.java`, `pipeline/.../transformation/processing/SensorConfigMessageHandler.java` — bei `null`-Value den DAS-Key aus dem Cache entfernen (nur im Hydration-Listener; der Reprocessing-Listener ignoriert Tombstones weiter). *Fertig:* Test zeigt: nach dem Tombstone fehlt der Key im Cache, ein folgender Messwert wird mit `sensor_parameter_id = NULL` geschrieben.
- [ ] **A5.3** `pipeline/.../transformation/processing/SensorDataBatchProcessor.java`, `pipeline/.../persistence/SensorReadingRepository.java` — TBD (hängt an der offenen Funktion): bei SQLSTATE `23503` die IDs des Batches einmal über `missing_sensor_parameter_ids` prüfen, fehlende auf `NULL` setzen und den Batch einmal wiederholen; scheitert auch das, greift die bestehende Retry-/DLT-Behandlung (`KafkaListenerConfig`). *Fertig:* Test mit einer gelöschten ID im Batch: alle Datensätze sind geschrieben, der betroffene mit `NULL`, nichts landet in `internal-normalized-dlt`.
- [ ] **A5.4** `pipeline/.../transformation/reprocessing/SensorReprocessingOrchestrator.java`, `SensorReadingRepository.bulkUpdate` — TBD wie A5.3: derselbe einmalige NULL-Retry für `UPDATE … FROM (VALUES …)`. *Fertig:* Test mit gelöschter ID während des Reprocessings läuft ohne Abbruch durch.

Hinweise zu A5:

- Konfigurationsnachrichten und Tombstones für einen DAS-Key haben denselben Key und landen daher geordnet in derselben Partition. Compaction- und Retention-Einstellungen des Topics bleiben wie sie sind; sie sind Sache der Infrastruktur.
- Der Tombstone gilt nur für den aktuellen DAS-Key. Cache-Einträge unter älteren DAS-Keys eines umgemappten Parameters werden nicht entfernt; für sie greift der NULL-Retry aus A5.3 (TBD). Das kostet einen zusätzlichen Lookup je betroffenem Batch und wird in Kauf genommen, weil unter alten Keys praktisch keine Messwerte mehr eintreffen.

### A6 — Test-Verdrahtung

- [ ] **A6.1** `core/src/test/java/ch/swisstopo/monteis/core/itconfig/TestcontainersConfiguration.java` — ein TimescaleDB-Container mit dem neuen Init, ein Flyway-Lauf auf `db/schema` und `db/seed`, ohne den Netzwerk-Alias für FDW (`withNetworkAliases(...)`) und ohne Placeholder `fdw_read_user`. *Fertig:* Core-ITs starten genau einen Datenbank-Container mit Seed-Daten.
- [ ] **A6.2** `pipeline/src/test/java/ch/swisstopo/monteis/pipeline/ITConfiguration/TestcontainersConfiguration.java` — ein Container mit dem neuen Init, Flyway wie heute nur auf `db/schema` (ohne Seed), ohne den Netzwerk-Alias für FDW (`withNetworkAliases(...)`) und ohne Placeholder `fdw_read_user`. *Fertig:* Pipeline-ITs grün gegen die Baseline.
- [ ] **A6.3** `core/src/test/java/ch/swisstopo/monteis/core/itconfig/SeedData.java`, `MeasurementQueryRepositoryIT.java`, `RowLevelSecurityIT.java` — keine neuen Testdaten-Skripte: `SeedData`, `PrivilegeLevel` und die ITs, die auf den Seed-Daten aufbauen (`MeasurementQueryRepositoryIT`, `OverviewQueryRepositoryIT`, `RowLevelSecurityIT`, `ExperimentDocumentServiceIT`, `SensorDocumentServiceIT`, `JooqSensorDocumentRepositoryIT`, `JooqSensorRepositoryAccessIT`, `JooqSensorRepositoryIT`), laufen auf dem zusammengeführten Seed weiter; die Kommentare in diesen drei Dateien, die auf `db/meta/seed` bzw. `db/timescale/seed` zeigen, zeigen auf `db/seed`. *Fertig:* diese ITs grün; im Core-Testbaum verweist nichts mehr auf die alten Seed-Ordner.
- [ ] **A6.4** `core/src/test/java/ch/swisstopo/monteis/core/E2ECoreApplication.java`, `webapp/playwright.config.ts`, `webapp/e2e/support/experiments.ts`, `webapp/e2e/support/organisation-table.ts`, `webapp/e2e/auth/role-concept.spec.ts` — ein Container; die Seed-Daten aus `db/seed` kommen wie heute über die Core-`TestcontainersConfiguration` (A6.1); den Kommentar „Booting 2x Postgres" in `playwright.config.ts` anpassen; die Kommentare der drei E2E-Dateien zeigen auf `db/seed/R__seed_dev_data.sql`. *Fertig:* Playwright-Lauf lokal grün; nichts unter `webapp/` verweist auf `db/meta/seed` oder `db/timescale/seed`.
- [ ] **A6.5** `core/src/main/resources/application-test.properties`, `application-e2e-test.properties`, `pipeline/src/main/resources/application-test.properties` — `meta.db.name`, `ts.db.name`, `meta.flyway.user.*`, `ts.flyway.user.*`, `fdw.read.user` durch `db.name` und `db.flyway.user.*` ersetzen; Datasource-Variablen wie A3. *Fertig:* kein `META_`, `TS_`, `FDW_` oder `fdw` mehr in diesen Dateien.

### A7 — CI, Image und Dokumentation

- [ ] **A7.1** `.github/workflows/pr-gate.yml` — `TS_*`, `META_*`, `FDW_READ_USER`, `CORE_APP_META_DB_*`, `PIPELINE_APP_TS_DB_*` auf die neuen Namen umstellen (Repository-Variablen entsprechend anlegen); `POSTGRES_DB_IMAGE` aus Warmup und Image-Cache entfernen. *Fertig:* PR-Gate grün, keine alten Variablen im Workflow.
- [ ] **A7.2** `docker/migration/Dockerfile` — eine Location: `COPY db/schema /flyway/sql/schema`; das Image kopiert `db/seed` nie; Kommentare anpassen (heute `db/*/seed`). *Fertig:* das Image enthält nur `V1__baseline.sql` und nichts aus `db/seed`.
- [ ] **A7.3** `.github/workflows/build-and-release-migration.yml` — Pfadfilter und `change_path` auf `db/schema/**` und `docker/migration/Dockerfile`; `db/seed` bleibt ausserhalb, Kommentare entsprechend. *Fertig:* eine Änderung unter `db/schema` löst den Build aus, eine unter `db/seed` oder `db/archive` nicht.
- [ ] **A7.4** `core/.../infrastructure/observability/QueryTimingListener.java` und `QueryTimingListenerTest.java` — FDW-Kommentare an das lokale Streaming anpassen. *Fertig:* kein „FDW" mehr in beiden Dateien.
- [ ] **A7.5** `documentation/architecture/src/05_building_block_view.adoc`, `06_runtime_view.adoc`, `08_concepts.adoc`, `09_architecture_decisions.adoc` (neue ADR „DB-Fusion, RLS auf Hypertable" mit dem Performance-Ergebnis aus T7), `documentation/architecture/images/overview.puml`, `erd-metadata.puml`, `core-api.puml`, `downsampling-sequence.puml` samt gerenderten SVGs; dazu `documentation/architecture/images/erd-relational.svg`, `deploy-aws-resources.drawio.svg`, `overview-1.svg` und `seq-downsampling.svg` (neu zeichnen bzw. neu exportieren, wo sie FDW, zwei Datenbanken oder alte Rollen zeigen) sowie `documentation/architecture/arc42-monteis.html` (aus den aktualisierten `.adoc`-Dateien neu erzeugen). *Fertig:* nach dem Neu-Rendern enthält `documentation/` keinen Bezug mehr auf FDW oder zwei Datenbanken.

## Tests

Die Tests laufen gegen einen Testcontainer mit der Baseline (A6). Testpfade relativ zu `core/src/test/java/ch/swisstopo/monteis/core/` bzw. `pipeline/src/test/java/ch/swisstopo/monteis/pipeline/`.

- [ ] **T1 RLS-Kontexte** — `infrastructure/security/RowLevelSecurityIT.java`, `infrastructure/jooq/RlsConnectionProviderTest.java`, `architecture/RowLevelSecurityTransactionTest.java`: verschiedene Keycloak-Gruppen- bzw. Experimentkontexte über denselben DB-User `core` sehen nur ihre Messwerte; ein ungesetzter Kontext sieht keine; ein Kontextwechsel überträgt keine Rechte auf die nächste Anfrage. *Fertig:* alle drei Tests grün gegen `sensor_reading`.
- [ ] **T2 Rechte positiv und negativ** — neu `infrastructure/security/DatabasePermissionsIT.java` (Core-Testbaum), ein Java-IT mit eigenen Prüfungen, läuft lokal und in CI: `core` hat CRUD auf Meta- und `jv_*`-Tabellen und `USAGE, SELECT` auf deren Sequenzen, nur SELECT auf `sensor_reading`; `pipeline` hat CRUD auf `sensor_reading`, aber keinen Zugriff auf Meta- und `jv_*`-Tabellen; ein direkter Select auf einen Chunk `_timescaledb_internal._hyper_*` scheitert für `core` und `pipeline`, während `core` über `sensor_reading` weiter liest (gefiltert durch RLS) und `pipeline` über `sensor_reading` weiter schreibt; keine Rolle ist Mitglied von `pg_read_all_data`/`pg_write_all_data`. TBD: `EXECUTE` von `pipeline` auf `missing_sensor_parameter_ids` nur, wenn die Funktion bleibt. *Fertig:* IT grün; er schlägt fehl, sobald ein Grant driftet.
- [ ] **T3 FK und Löschen** — neu `modules/sensor/jooq/SensorParameterDeletionIT.java`: als `core` einen Parameter mit Messwerten löschen; die Messwerte bleiben mit `NULL`-Zuordnung erhalten und sind für `core` unsichtbar, ohne dass `core` UPDATE auf `sensor_reading` braucht; eine unbekannte Nicht-NULL-ID wird mit `23503` abgewiesen, `NULL` wird angenommen. *Fertig:* IT grün.
- [ ] **T4 Tombstone und Race** — Tombstone-Teil: `infrastructure/kafka/SensorConfigPublisherTest.java` und ein Service-Test zu A5.1 (Core), der Test zu A5.2 (Pipeline): nach dem Löschen eines Parameters entfernt der Tombstone den Cache-Eintrag, und neue Messwerte für seinen DAS-Key werden mit `sensor_parameter_id = NULL` gespeichert. Race-Teil, TBD (hängt am NULL-Retry aus A5.3): `transformation/processing/SensorDataBatchProcessorTest.java` und `persistence/SensorReadingRepositoryIT.java` (Pipeline): ein Messwert, der mit der alten ID schon unterwegs ist, lässt den Batch nicht scheitern und schickt keinen gültigen Datensatz in `internal-normalized-dlt`. *Fertig:* Tests des Tombstone-Teils grün, inkl. Rollback-Fall ohne Tombstone; der Race-Teil erst nach der Entscheidung.
- [ ] **T5 Pipeline ohne Benutzerkontext** — `persistence/SensorReadingRepositoryIT.java`: Upsert (`ON CONFLICT DO UPDATE`), `UPDATE … FROM (VALUES …)` und die Reprocessing-Selects laufen als `pipeline` bei aktiver RLS. *Fertig:* IT grün als Rolle `pipeline`, nicht als Superuser.
- [ ] **T6 Bestehende ITs** — `modules/measurement/jooq/MeasurementQueryRepositoryIT.java`, `modules/overview/jooq/OverviewQueryRepositoryIT.java`, `infrastructure/security/RowLevelSecurityIT.java` laufen gegen die lokale Tabelle; der umbenannte Leak-Test aus A4.6 bleibt erhalten. *Fertig:* alle grün, Spalten-Mapping (`das_key`, `status` als Enum) geprüft.
- [ ] **T7 Performance-Vergleich** — `documentation/architecture/src/09_architecture_decisions.adoc` (Ergebnis in der ADR aus A7.5): `EXPLAIN ANALYZE` der drei heutigen FDW-Lesepfade, nämlich Messwerttabelle mit letztem Messwert je Parameter (`MeasurementQueryRepository`, `LATEST_READINGS`), Range-Scan `findChartData` und Overview `ORDER BY timestamp DESC LIMIT n` (`OverviewQueryRepository`), je für einen Benutzer mit allen Experimenten und einen eingeschränkten Benutzer mit wenigen Experimenten. Vorher-Messung auf dem heutigen Stand vor A2, Nachher-Messung nach A4, beide auf denselben lokalen Daten (per `generate_series` erzeugt; das Skript liegt dem Ergebnis bei und ist keine Flyway-Location). *Fertig:* Vorher-/Nachher-Tabelle ist dokumentiert. Kein Zahlenwert blockiert den Merge.

## Umstellung AWS

Koordinierte Umstellung je Umgebung, zuerst dev, danach die weiteren. Nichts aus den alten AWS-Datenbanken wird übernommen, auch nicht als Kopie zum Debuggen.

1. Bis zur Umstellung läuft die heutige App-Version unverändert auf dem alten Setup. Die neue Version und die Baseline werden vorher nicht auf AWS ausgerollt; weil Merges automatisch nach staging deployen, wird erst zur Umstellung gemergt oder das Deployment bis dahin gesperrt.
2. Das Infra-Team stellt je Umgebung eine leere Datenbank mit den drei Rollen bereit und bestätigt das.
3. Die alte App (Core und Pipeline) wird gestoppt. `internal-sensor-config` wird neu angelegt (leer), und die Consumer-Offsets der Pipeline auf `internal-normalized` werden auf `latest` gesetzt.
4. Das neue Migration-Image führt Flyway gegen die leere Datenbank aus.
5. Scheitert die Baseline, startet wieder die alte App auf dem alten Setup; Core veröffentlicht die Konfigurationen über den bestehenden Republish-Endpunkt (`SensorController`) neu. Der nächste Versuch läuft auf einer frisch geleerten Datenbank, ohne Reparatur vor Ort.
6. Erst nach erfolgreicher Baseline starten die neuen Versionen von Core und Pipeline; damit ist die Umstellung abgeschlossen. Messwerte, die eintreffen, bevor Sensoren neu angelegt sind, werden mit `sensor_parameter_id = NULL` gespeichert; der Backfill ordnet sie zu, sobald ein Parameter mit demselben DAS-Key existiert.

## Hinweis Infrastruktur

Änderungen an Infrastruktur und AWS sind Sache des Infra-Teams und nicht Teil dieses Plans. Die App erwartet je Umgebung: eine leere PostgreSQL-18-Datenbank `monteis` mit TimescaleDB, Owner `monteis_ts`, mit nur dem Schema `public`; die Rollen `monteis_ts` (Owner, Flyway), `core` und `pipeline`, keine davon Mitglied von `pg_read_all_data` oder `pg_write_all_data`; dieselben Default Privileges von `monteis_ts` an `core` wie in `docker/dataset-init/init.sql`, also `SELECT, INSERT, UPDATE, DELETE` auf Tabellen und `USAGE, SELECT` auf Sequenzen, und keine für `pipeline`; die neuen Variablennamen aus A3; ein Migration-Image mit nur einer Location (A7.2). Dass RDS entfällt und die Keycloak-Datenbank in denselben Postgres-Cluster zieht, entscheidet und plant das Infra-Team.

## Abnahme

- [ ] Ein frisches lokales Setup aus leerer Datenbank funktioniert reproduzierbar mit `docker/compose.yml` und `docker/dataset-init/init.sql`.
- [ ] Core und Pipeline laufen gegen dieselbe Datenbank (`core/src/main/resources/application-dev.properties`, `pipeline/src/main/resources/application-dev.properties`).
- [ ] Codegen (`core/pom.xml`, `pipeline/pom.xml`) und CI (`.github/workflows/pr-gate.yml`) sind grün; T1–T6 grün (T4 Race-Teil: TBD).
- [ ] In `lg-monteis` gibt es ausserhalb von `db/archive/` und dieser Datei keinen Bezug mehr auf FDW, `db/meta/`, `db/timescale/`, `TS_*`, `META_*`, `FDW_*` oder die Rollen `fdw_user`, `ts_flyway`, `core_flyway`, `core_app`, `pipeline_app`.
- [ ] Der Performance-Vergleich (T7) ist in `documentation/architecture/src/09_architecture_decisions.adoc` dokumentiert.
- [ ] Die arc42-Dokumentation unter `documentation/architecture/` ist aktualisiert (A7.5).

## Risiken

- **GRANT-Drift:** Default Privileges gelten pro Ersteller-Rolle; ein vergessener `REVOKE` gibt `core` still Schreibrecht. Gegenmittel: T2 als Regressionstest, lokal und in CI. Falsche Rechte in AWS deckt dieser Plan nicht ab.
- **Direkter Chunk-Zugriff:** die Grants der Hypertable gehen auf die Chunks über, für `core` und `pipeline`, RLS nicht. Gegenmittel: die Baseline entzieht `USAGE` auf `_timescaledb_internal`, T2 prüft es. Brechen dadurch Lesen oder Schreiben über `sensor_reading`, fällt der Entzug heraus, und der direkte Chunk-Zugriff bleibt als akzeptiertes Risiko bestehen.
- **Veraltete Cache-IDs und Löschrennen:** ohne A5 scheitern Schreibvorgänge trotz `ON DELETE SET NULL`. Gegenmittel: Tombstone, Eviction und NULL-Retry (A5; der NULL-Retry ist TBD), getestet in T4 (Race-Teil TBD).
- **Unsichtbare Messwerte nach dem Löschen:** sie bleiben erhalten, aber `core` sieht sie nicht mehr; legt jemand unter demselben DAS-Key wieder einen Parameter an, erhält dieser sie per Backfill. Das ist gewollt und steht in der ADR.
- **Löschkosten:** `ON DELETE SET NULL` aktualisiert unter Umständen viele Messwerte über mehrere Chunks. Den Index `(sensor_parameter_id, timestamp DESC)` behalten und die Laufzeit in T3 beobachten.
- **Unvollständige Baseline:** eine beim Zusammenfassen vergessene Policy, Funktion oder ein vergessener Index fällt erst spät auf. Gegenmittel: Objektvergleich in A1.1 und T1–T6 auf frischer Baseline.
- **Keine Workload-Isolation mehr:** Ingest und Lesen teilen sich die Ressourcen einer Datenbank; unter hoher Datenrate beobachten.
- **Seed driftet vom Schema:** eine spätere `V2__…` kann `db/seed/R__seed_dev_data.sql` brechen. Gegenmittel: die Core-ITs laufen mit Seed (A6.1) und schlagen dann fehl (T6).
- **Seed in AWS:** das `TRUNCATE … CASCADE` des Seeds würde Echtdaten löschen. Gegenmittel: das Migration-Image enthält `db/seed` nicht (A7.2), und eine Änderung daran löst keinen Image-Build aus (A7.3).

