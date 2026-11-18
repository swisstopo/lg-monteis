# Migrationsplan: Fusion Meta-DB + TimescaleDB

Die Meta-DB und die TimescaleDB werden zu **einer** Datenbank mit **einem** Schema `public` zusammengeführt; Die FDW-Extension entfällt, weil dies der Engpass beim Lesen der Zeitreihen ist. Dieser Plan deckt nur den App-Teil (`lg-monteis`) ab. Jede Umgebung startet mit einer leeren Datenbank; übernommen wird nur das Flyway-DDL. In Testcontainers spielt Flyway zusätzlich die Seed-Daten aus `db/seed` ein.

Referenz (im Workspace, nicht wiederholt): welche Dateien, Rollen und Variablen an der Zwei-DB-Topologie hängen, steht in der [Database Dependency Map](../aidlc/spaces/default/codekb/lg-monteis/dependencies.md#database-dependency-map-input-for-the-planned-db-merge); Schema, Rollen und RLS-Kontext heute in [architecture.md](../aidlc/spaces/default/codekb/lg-monteis/architecture.md#data-layer-current-state).

**Neu**

- Soft-Delete für Sensor-Parameter (A5): `sensor_parameter.deleted_at`, Filter in allen Lesepfaden von `core`, partieller Unique-Index für `(sensor_id, das_parameter_alias)`. Kein Wiederherstellen.
- Die Bereinigung soft-gelöschter Parameter (Tombstone und Cache-Eviction als Voraussetzung, Messwerte auf `NULL`, danach hartes Löschen) ist ein Folgeticket, siehe Abschnitt „Folgeticket: Bereinigung gelöschter Sensor-Parameter".
- Kafka-Reset bei der Umstellung: `internal-sensor-config` wird neu angelegt, die Offsets auf `internal-normalized` werden auf `latest` gesetzt.
- Rechte auf die JaVers-Tabellen `jv_*` stehen explizit in der Rechtematrix und im Permissions-IT.
- FDW-Kommentare in `QueryTimingListener` und der Leak-Test „through the secured view" in `MeasurementQueryRepositoryIT` werden angepasst.
- Die Seed-Daten bleiben für lokale Tests: `db/meta/seed` und `db/timescale/seed` werden zu einer Location `db/seed` zusammengeführt, die nur lokal und in Testcontainers läuft, nie in AWS.
- Lokal bleibt die TimescaleDB (`ts_db`, `flyway-timescale`) und nimmt die Meta-Tabellen auf; `metadata_db` und `flyway-metadata` entfallen (A2.2).

**Entschieden**

- Gelöschte Sensor-Parameter werden in diesem Ticket nur soft gelöscht; ihre Messwerte bleiben erhalten. Es gibt kein Wiederherstellen. Die Bereinigung per Cron-Job folgt in einem eigenen Ticket.

## Zielbild

- Eine PostgreSQL-18-Datenbank `monteis` mit TimescaleDB, Owner `monteis_ts`, ein einziges Schema `public`. Der Name gilt in jeder Umgebung (lokal, Testcontainers, CI, AWS). Weil `public` seit PostgreSQL 15 der Rolle `pg_database_owner` gehört, ist damit auch `monteis_ts` Owner von `public`. Lokal und in Tests das Image `timescale/timescaledb:latest-pg18`.
- Keine FDW-Objekte: weder Extension `postgres_fdw` noch Server `timescale_server`, User Mapping oder Foreign Table `raw_sensor_reading`.
- Kein View `sensor_reading_secured`.
- `sensor_reading` ist eine lokale Hypertable mit nativer RLS (`ENABLE ROW LEVEL SECURITY`, zwei Policies, siehe SQL unten).
- FK `sensor_reading.sensor_parameter_id → sensor_parameter.id ON DELETE SET NULL`. `NULL` ist erlaubt; eine unbekannte Nicht-NULL-ID weist die Datenbank mit `23503` ab. In diesem Ticket löscht die App keinen Parameter hart; der FK ist die Grundlage für das Folgeticket.
- Soft-Delete: Ein entfernter Parameter bekommt `deleted_at = now()` und bleibt als Zeile bestehen. `core` zeigt ihn nirgends mehr an, und seine Messwerte sind über `sensor_reading_read` unsichtbar. Die Pipeline erfährt in diesem Ticket nichts davon: Ihr Cache behält die ID, und neue Messwerte seines DAS-Keys werden weiter mit ihr geschrieben. Der FK ist erfüllt, weil die Zeile existiert, und `core` sieht diese Messwerte ebenfalls nicht.
- Jede Umgebung (lokal, Testcontainers, CI, AWS) startet leer. Flyway läuft als `monteis_ts` und wendet `db/schema/V1__baseline.sql` an; spätere Änderungen folgen ab `V2__…`. In AWS ist `db/schema` die einzige Location, die Datenbank bleibt ohne Daten. Lokal (geseedete Make-Ziele, Compose) und in Testcontainers (Core-ITs, E2E) kommt die repeatable Location `db/seed` dazu. Flyway bleibt ausserhalb der Apps (Migration-Image, Jobs, Tests und Codegen programmatisch).
- Backfill: Das Reprocessing ordnet Messwerte mit `sensor_parameter_id = NULL` dem aktuellen Parameter mit demselben DAS-Key zu. Wird unter dem DAS-Key eines soft-gelöschten Parameters ein neuer Parameter angelegt, überschreibt dessen Config den Cache-Eintrag; ab dann erhält er die neuen Messwerte. Messwerte, die zwischen Soft-Delete und Neuanlage geschrieben wurden, tragen die ID des gelöschten Parameters, sind für den Backfill (nur `NULL`) nicht erreichbar und werden erst bei der Bereinigung im Folgeticket zugeordnet.

### Erstellung `db/schema/V1__baseline.sql`

- Meta-Teil aus einem Schema-only-Export eines frischen Setups auf Stand Meta V23 / TimescaleDB V4. Entfernt werden: Extension `postgres_fdw`, Server `timescale_server`, User Mapping, Foreign Table `raw_sensor_reading`, View `sensor_reading_secured`, Owner-Angaben und alle Grants an alte Rollen.
- Erhalten bleiben: RLS-Funktionen (`can_access_experiment`, `can_access_sensor`, `can_write_experiment`, `can_write_an_experiment_of_sensor`), alle Policies, die JaVers-Tabellen und -Sequenzen aus Meta V4, die Enums `unit` und `range_category`, alle Indizes und die weiterhin gültigen Kommentare (Index-Begründungen aus TS V1/V3, RLS-Begründungen aus Meta V6/V10).
- Geändert gegenüber dem Export (Soft-Delete, A5): `sensor_parameter` bekommt `deleted_at TIMESTAMPTZ` (nullable). Der Unique-Constraint `(sensor_id, das_parameter_alias)` aus Meta V9 wird durch einen partiellen Unique-Index ersetzt, damit ein Parameter mit demselben Alias neu angelegt werden kann, solange der gelöschte noch existiert. Die Policy `sensor_parameter_read` bleibt unverändert (Begründung unter „Hinweise zu A5"):

```sql
ALTER TABLE sensor_parameter ADD COLUMN deleted_at TIMESTAMPTZ;
-- ersetzt UNIQUE (sensor_id, das_parameter_alias) aus Meta V9
CREATE UNIQUE INDEX sensor_parameter_sensor_alias_active_key
    ON sensor_parameter (sensor_id, das_parameter_alias)
    WHERE deleted_at IS NULL;
```

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
-- transitiv. NULL-Zuordnungen matchen nie und bleiben unsichtbar, ebenso Messwerte
-- soft-gelöschter Parameter (deleted_at IS NOT NULL).
ALTER TABLE sensor_reading ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensor_reading_read ON sensor_reading FOR SELECT TO core
    USING (sensor_parameter_id IN (SELECT id FROM sensor_parameter WHERE deleted_at IS NULL));

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
```

- Alte Migrationen ziehen nach `db/archive/meta` und `db/archive/timescale` um und liegen damit ausserhalb jeder Flyway-Location.
- Seeds: `db/meta/seed/R__seed_dev_data.sql` und `db/timescale/seed/R__seed_dev_data.sql` werden zu `db/seed/R__seed_dev_data.sql` zusammengeführt, zuerst der Meta-Teil, danach `sensor_reading`, weil der FK die `sensor_parameter`-Zeilen voraussetzt. Das `TRUNCATE … CASCADE` des Meta-Teils leert über den FK jetzt auch `sensor_reading`. Der Kommentar, die IDs zwischen zwei Datenbanken von Hand synchron zu halten, entfällt.

## Rollen und Rechte

Die DB-Rollen sind technische Benutzer, keine fachlichen Identitäten. Welche Experimente und Messwerte ein angemeldeter Benutzer sieht, entscheidet RLS anhand des Kontexts, den `RlsConnectionProvider` aus den Keycloak-Gruppen setzt (siehe [architecture.md, Row-level security context](../aidlc/spaces/default/codekb/lg-monteis/architecture.md#row-level-security-context)).

|    Rolle     |                                      Meta-Tabellen                                      |                 JaVers `jv_*` (4 Tabellen, 3 Sequenzen)                  |                         `sensor_reading`                         | Chunks `_timescaledb_internal._hyper_*` |
|--------------|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------|------------------------------------------------------------------|-----------------------------------------|
| `monteis_ts` | Owner, DDL (Flyway)                                                                     | Owner                                                                    | Owner, DDL                                                       | Owner (über TimescaleDB)                |
| `core`       | SELECT, INSERT, UPDATE, DELETE; `USAGE, SELECT` auf Sequenzen; RLS je Tabelle wie heute | SELECT, INSERT, UPDATE, DELETE; `USAGE, SELECT` auf Sequenzen; keine RLS | nur SELECT, gefiltert durch `sensor_reading_read`                | kein direkter Zugriff                   |
| `pipeline`   | kein Zugriff                                                                            | kein Zugriff                                                             | SELECT, INSERT, UPDATE, DELETE, Policy `sensor_reading_pipeline` | kein direkter Zugriff                   |

- `core` und `pipeline` erhalten `USAGE` auf `public`. Keine Rolle ist Mitglied von `pg_read_all_data` oder `pg_write_all_data`; es gelten nur explizite Grants.
- Die Rechte von `core` auf Meta- und `jv_*`-Tabellen kommen aus `ALTER DEFAULT PRIVILEGES FOR ROLE monteis_ts IN SCHEMA public`: `SELECT, INSERT, UPDATE, DELETE` auf Tabellen und `USAGE, SELECT` auf Sequenzen, jeweils an `core`. Deshalb entzieht die Baseline `core` auf `sensor_reading` alle Rechte (`REVOKE ALL`) und vergibt danach nur `SELECT`.
- `pipeline` erhält keine Default Privileges, nur die expliziten Grants auf `sensor_reading` aus der Baseline; sonst bekäme die Pipeline CRUD auf alle Meta-Tabellen.
- Die Policy `sensor_reading_pipeline` filtert nicht, wirkt aber nur innerhalb dieser Grants.
- Chunks: TimescaleDB überträgt die Grants der Hypertable auf die Chunks, also `SELECT` an `core` und `SELECT, INSERT, UPDATE, DELETE` an `pipeline`; die RLS-Policies greifen beim direkten Chunk-Select aber nicht. Deshalb entzieht die Baseline `PUBLIC`, `core` und `pipeline` das Recht `USAGE` auf das Schema `_timescaledb_internal` (Schritt 3); die RLS sitzt auf `sensor_reading` und ist davon nicht betroffen. Der Permissions-IT T2 prüft, dass ein direkter Chunk-Select für beide Rollen scheitert, `core` über `sensor_reading` weiter liest (gefiltert durch RLS) und `pipeline` über `sensor_reading` weiter schreibt. Brechen Lesen oder Schreiben über die Hypertable, fällt der Entzug aus `V1__baseline.sql` wieder heraus, und der direkte Chunk-Zugriff wird als Risiko akzeptiert (siehe Risiken).

## Phasen

Jede Aufgabe ist ein Ticket. Pfade relativ zu `lg-monteis/`; `core/.../` steht für `core/src/main/java/ch/swisstopo/monteis/core/`, `pipeline/.../` für `pipeline/src/main/java/ch/swisstopo/monteis/pipeline/`. Neue Variablennamen: `DB_NAME` (Wert `monteis`), `DB_FLYWAY_USER/PWD` (Rolle `monteis_ts`), `CORE_DB_USER/PWD` (`core`), `PIPELINE_DB_USER/PWD` (`pipeline`), `CORE_DB_JDBC_URL` (prod).

### A1 — Baseline

- [ ] **A1.1** `db/schema/V1__baseline.sql` — Baseline wie unter „Erstellung" beschrieben schreiben, inklusive `sensor_parameter.deleted_at`, partiellem Unique-Index und dem Filter `deleted_at IS NULL` in `sensor_reading_read`. *Fertig:* Flyway wendet sie als `monteis_ts` fehlerfrei auf eine leere Datenbank an; das Ergebnis enthält kein FDW-Objekt und keinen View `sensor_reading_secured`; ein Objektvergleich (Funktionen, Policies, Indizes, Enums) mit einem frischen Setup auf heutigem Stand zeigt nur die gewollten Unterschiede.
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
- [ ] **A4.2** `pipeline/pom.xml` — Testcontainer mit dem neuen Init, Flyway auf `db/schema` ohne Placeholder, `<includes>sensor_reading|range_category</includes>` im jOOQ-Codegen. *Fertig:* die Pipeline generiert nur diese beiden Objekte.
- [ ] **A4.3** `core/.../modules/measurement/jooq/MeasurementQueryRepository.java` — `SENSOR_READING_SECURED` → `SENSOR_READING`; `DSL.inline(...)` für `from`/`to`/IDs durch Bind-Werte ersetzen; `status` als `RangeCategory` statt `String` lesen; die beiden Kommentare, die auf `db/meta/schema/V14` bzw. `db/meta/schema/V13` zeigen (heute Zeilen 60 und 156), verweisen auf `V1__baseline.sql`. *Fertig:* kein Import von `SensorReadingSecured`, kein `DSL.inline` mehr für Pushdown, kein Bezug auf `db/meta/` in der Datei.
- [ ] **A4.4** `core/.../modules/measurement/jooq/SensorDetailProjection.java` — Felder aus `SENSOR_READING`, `status` als Enum. *Fertig:* kein `String.class` mehr für `status`.
- [ ] **A4.5** `core/.../modules/overview/jooq/OverviewQueryRepository.java` — liest `SENSOR_READING`; Kommentar zum lokalen LIMIT und `fetch_size` an die neue Lage anpassen. *Fertig:* kein Bezug auf den View oder FDW mehr in der Klasse.
- [ ] **A4.6** `core/src/test/java/ch/swisstopo/monteis/core/modules/measurement/jooq/MeasurementQueryRepositoryIT.java` — liest `SENSOR_READING`; der Test `should_not_leak_readings_of_invisible_sensors_through_the_secured_view` wird in `..._through_row_level_security` umbenannt und bleibt erhalten. *Fertig:* IT grün, kein Bezug auf den View.

### A5 — Soft-Delete von Sensor-Parametern

- [ ] **A5.1** `core/.../modules/sensor/jooq/JooqSensorRepository.java` — `update` ermittelt `idsToRemove` nur aus Parametern mit `deleted_at IS NULL` und setzt für sie `deleted_at = now()` statt `deleteFrom(SENSOR_PARAMETER)`; es liefert die entfernten Parameter (Zustand vor dem Update) zurück. Eine mitgeschickte ID eines soft-gelöschten Parameters belebt ihn nicht wieder. *Fertig:* IT zeigt: nach dem Entfernen existiert die Zeile mit gesetztem `deleted_at`, ein erneutes Speichern ändert `deleted_at` nicht, und ein neuer Parameter mit demselben Alias am selben Sensor lässt sich anlegen.
- [ ] **A5.2** `core/.../modules/sensor/jooq/JooqSensorRepository.java` (alle `find*`/`streamAllSensors`), `JooqSensorParameterRowQueryRepository.java`, `SensorRecordProjections.java`, `JooqSensorCsvExportQueryRepository.java`, `core/.../modules/measurement/jooq/MeasurementQueryRepository.java`, `SensorDetailProjection.java`, `core/.../modules/overview/jooq/OverviewQueryRepository.java` — jeder Lesezugriff auf `SENSOR_PARAMETER` filtert `deleted_at IS NULL`. Damit überspringt auch `SensorService.republishAllActiveParameterConfigs` gelöschte Parameter. *Fertig:* je Lesepfad ein IT, der einen soft-gelöschten Parameter nicht liefert; kein Zugriff auf `SENSOR_PARAMETER` in `core` ohne diesen Filter.
  Hinweise zu A5:
- Der Filter sitzt im Code und nicht in der Policy `sensor_parameter_read`: PostgreSQL prüft bei einem `UPDATE`, das die Tabelle liest (`WHERE id = ?`), die neue Zeile auch gegen die SELECT-Policy. Ein `SET deleted_at = now()` würde dann mit „new row violates row-level security policy" abgewiesen. Für die Messwerte genügt der Filter im Subselect von `sensor_reading_read`, weil `core` `sensor_reading` nie schreibt.
- Kein Tombstone, keine Cache-Eviction: Solange kein Parameter hart gelöscht wird, kann eine ID im Pipeline-Cache den FK nicht verletzen. Die Pipeline schreibt Messwerte eines soft-gelöschten Parameters weiter mit seiner ID; das ist gewollt, `core` sieht sie nicht, und das Folgeticket setzt sie bei der Bereinigung auf `NULL`. Tombstone und Eviction sind Voraussetzung für das harte Löschen und kommen deshalb mit dem Folgeticket.
- Das UI ändert sich nicht: Der Button „Entfernen" bleibt, nur das Backend löscht soft.

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
- [ ] **A7.5** `documentation/architecture/src/05_building_block_view.adoc`, `06_runtime_view.adoc`, `08_concepts.adoc`, `09_architecture_decisions.adoc` (neue ADR „DB-Fusion, RLS auf Hypertable" mit dem Performance-Ergebnis aus T6), `documentation/architecture/images/overview.puml`, `erd-metadata.puml`, `core-api.puml`, `downsampling-sequence.puml` samt gerenderten SVGs; dazu `documentation/architecture/images/erd-relational.svg`, `deploy-aws-resources.drawio.svg`, `overview-1.svg` und `seq-downsampling.svg` (neu zeichnen bzw. neu exportieren, wo sie FDW, zwei Datenbanken oder alte Rollen zeigen) sowie `documentation/architecture/arc42-monteis.html` (aus den aktualisierten `.adoc`-Dateien neu erzeugen). *Fertig:* nach dem Neu-Rendern enthält `documentation/` keinen Bezug mehr auf FDW oder zwei Datenbanken.

## Tests

Die Tests laufen gegen einen Testcontainer mit der Baseline (A6). Testpfade relativ zu `core/src/test/java/ch/swisstopo/monteis/core/` bzw. `pipeline/src/test/java/ch/swisstopo/monteis/pipeline/`.

- [ ] **T1 RLS-Kontexte** — `infrastructure/security/RowLevelSecurityIT.java`, `infrastructure/jooq/RlsConnectionProviderTest.java`, `architecture/RowLevelSecurityTransactionTest.java`: verschiedene Keycloak-Gruppen- bzw. Experimentkontexte über denselben DB-User `core` sehen nur ihre Messwerte; ein ungesetzter Kontext sieht keine; ein Kontextwechsel überträgt keine Rechte auf die nächste Anfrage. *Fertig:* alle drei Tests grün gegen `sensor_reading`.
- [ ] **T2 Rechte positiv und negativ** — neu `infrastructure/security/DatabasePermissionsIT.java` (Core-Testbaum), ein Java-IT mit eigenen Prüfungen, läuft lokal und in CI: `core` hat CRUD auf Meta- und `jv_*`-Tabellen und `USAGE, SELECT` auf deren Sequenzen, nur SELECT auf `sensor_reading`; `pipeline` hat CRUD auf `sensor_reading`, aber keinen Zugriff auf Meta- und `jv_*`-Tabellen; ein direkter Select auf einen Chunk `_timescaledb_internal._hyper_*` scheitert für `core` und `pipeline`, während `core` über `sensor_reading` weiter liest (gefiltert durch RLS) und `pipeline` über `sensor_reading` weiter schreibt; keine Rolle ist Mitglied von `pg_read_all_data`/`pg_write_all_data`. *Fertig:* IT grün; er schlägt fehl, sobald ein Grant driftet.
- [ ] **T3 Soft-Delete und FK** — neu `modules/sensor/jooq/SensorParameterDeletionIT.java`: als `core` einen Parameter mit Messwerten über `update` entfernen; die Zeile bleibt mit `deleted_at`, der Parameter und seine Messwerte sind für `core` unsichtbar; ein Messwert mit seiner ID wird als `pipeline` weiterhin angenommen. FK auf DB-Ebene (Grundlage für das Folgeticket): eine unbekannte Nicht-NULL-ID wird mit `23503` abgewiesen, `NULL` wird angenommen. *Fertig:* IT grün.
- [ ] **T4 Pipeline ohne Benutzerkontext** — `persistence/SensorReadingRepositoryIT.java`: Upsert (`ON CONFLICT DO UPDATE`), `UPDATE … FROM (VALUES …)` und die Reprocessing-Selects laufen als `pipeline` bei aktiver RLS. *Fertig:* IT grün als Rolle `pipeline`, nicht als Superuser.
- [ ] **T5 Bestehende ITs** — `modules/measurement/jooq/MeasurementQueryRepositoryIT.java`, `modules/overview/jooq/OverviewQueryRepositoryIT.java`, `infrastructure/security/RowLevelSecurityIT.java` laufen gegen die lokale Tabelle; der umbenannte Leak-Test aus A4.6 bleibt erhalten. *Fertig:* alle grün, Spalten-Mapping (`das_key`, `status` als Enum) geprüft.
- [ ] **T6 Performance-Vergleich** — `documentation/architecture/src/09_architecture_decisions.adoc` (Ergebnis in der ADR aus A7.5): `EXPLAIN ANALYZE` der drei heutigen FDW-Lesepfade, nämlich Messwerttabelle mit letztem Messwert je Parameter (`MeasurementQueryRepository`, `LATEST_READINGS`), Range-Scan `findChartData` und Overview `ORDER BY timestamp DESC LIMIT n` (`OverviewQueryRepository`), je für einen Benutzer mit allen Experimenten und einen eingeschränkten Benutzer mit wenigen Experimenten. Vorher-Messung auf dem heutigen Stand vor A2, Nachher-Messung nach A4, beide auf denselben lokalen Daten (per `generate_series` erzeugt; das Skript liegt dem Ergebnis bei und ist keine Flyway-Location). *Fertig:* Vorher-/Nachher-Tabelle ist dokumentiert. Kein Zahlenwert blockiert den Merge.

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
- [ ] Codegen (`core/pom.xml`, `pipeline/pom.xml`) und CI (`.github/workflows/pr-gate.yml`) sind grün; T1–T5 grün.
- [ ] In `lg-monteis` gibt es ausserhalb von `db/archive/` und dieser Datei keinen Bezug mehr auf FDW, `db/meta/`, `db/timescale/`, `TS_*`, `META_*`, `FDW_*` oder die Rollen `fdw_user`, `ts_flyway`, `core_flyway`, `core_app`, `pipeline_app`.
- [ ] Der Performance-Vergleich (T6) ist in `documentation/architecture/src/09_architecture_decisions.adoc` dokumentiert.
- [ ] Die arc42-Dokumentation unter `documentation/architecture/` ist aktualisiert (A7.5).

## Risiken

- **GRANT-Drift:** Default Privileges gelten pro Ersteller-Rolle; ein vergessener `REVOKE` gibt `core` still Schreibrecht. Gegenmittel: T2 als Regressionstest, lokal und in CI. Falsche Rechte in AWS deckt dieser Plan nicht ab.
- **Direkter Chunk-Zugriff:** die Grants der Hypertable gehen auf die Chunks über, für `core` und `pipeline`, RLS nicht. Gegenmittel: die Baseline entzieht `USAGE` auf `_timescaledb_internal`, T2 prüft es. Brechen dadurch Lesen oder Schreiben über `sensor_reading`, fällt der Entzug heraus, und der direkte Chunk-Zugriff bleibt als akzeptiertes Risiko bestehen.
- **Veraltete Cache-IDs:** die Pipeline schreibt weiter unter der ID eines soft-gelöschten Parameters. In diesem Ticket unschädlich, weil die Zeile bleibt und der FK erfüllt ist (T3). Vor dem ersten harten Löschen müssen Tombstone und Eviction aus dem Folgeticket ausgerollt sein, sonst scheitern Batches mit `23503`.
- **Vergessener Filter:** ein Lesepfad ohne `deleted_at IS NULL` zeigt gelöschte Parameter wieder an. Gegenmittel: je Lesepfad ein IT (A5.2); die Messwerte bleiben über `sensor_reading_read` trotzdem unsichtbar.
- **Unsichtbare Messwerte nach dem Löschen:** sie bleiben erhalten, aber `core` sieht sie nicht mehr; legt jemand unter demselben DAS-Key wieder einen Parameter an, erhält dieser die Messwerte ab seiner Anlage; die dazwischen geschriebenen tragen die alte ID und werden erst nach der Bereinigung im Folgeticket zugeordnet. Das ist gewollt und steht in der ADR.
- **Soft-gelöschte Zeilen wachsen an:** bis zum Folgeticket bleiben gelöschte Parameter und ihre Zuordnungen bestehen. Bei der heutigen Löschhäufigkeit unkritisch.
- **Unvollständige Baseline:** eine beim Zusammenfassen vergessene Policy, Funktion oder ein vergessener Index fällt erst spät auf. Gegenmittel: Objektvergleich in A1.1 und T1–T5 auf frischer Baseline.
- **Keine Workload-Isolation mehr:** Ingest und Lesen teilen sich die Ressourcen einer Datenbank; unter hoher Datenrate beobachten.
- **Seed driftet vom Schema:** eine spätere `V2__…` kann `db/seed/R__seed_dev_data.sql` brechen. Gegenmittel: die Core-ITs laufen mit Seed (A6.1) und schlagen dann fehl (T5).
- **Seed in AWS:** das `TRUNCATE … CASCADE` des Seeds würde Echtdaten löschen. Gegenmittel: das Migration-Image enthält `db/seed` nicht (A7.2), und eine Änderung daran löst keinen Image-Build aus (A7.3).

## Folgeticket: Bereinigung gelöschter Sensor-Parameter -> MON-265

