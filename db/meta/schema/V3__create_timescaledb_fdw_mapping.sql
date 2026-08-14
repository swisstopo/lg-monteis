-- Foreign table mapping CoreAPI's view of TimescaleDB's sensor_reading
-- Lives in PostgresDB's migrations (FDW mapping DDL belongs with the consumer, not the producer)

-- use_remote_estimate: lets the planner ask TimescaleDB for real cost estimates, which is what
-- makes it choose a remote sorted path (ORDER BY pushed down) instead of sorting locally. Costs
-- one extra remote EXPLAIN per planning pass. Turning it off measured 53 ms -> 276 ms on a 52k-row
-- range scan, because the ORDER BY stopped being pushed down.
--
-- fetch_size: the postgres_fdw cursor batch size, and the only lever there is on how many rows
-- cross the wire for a LIMIT query.
--
-- postgres_fdw pushes LIMIT down on this foreign table just fine. What blocks it is the join to
-- the local sensors table inside sensor_reading_secured (V6): once a local relation joins in, the
-- final relation is no longer foreign, so LIMIT is applied locally.
--
--   SELECT * FROM raw_sensor_reading     ORDER BY "timestamp" DESC LIMIT 100  -> LIMIT pushed
--   SELECT * FROM sensor_reading_secured ORDER BY "timestamp" DESC LIMIT 100  -> LIMIT local
--
-- So fetch_size is what bounds a LIMIT read: the cursor fetches exactly one batch and closes.
-- Confirmed on the wire with log_statement='all' on TimescaleDB:
--
--   overview LIMIT 100              -> DECLARE c1, one FETCH 2000, CLOSE c1
--   52,128-row scan, fetch_size 2000 ->  27 x FETCH 2000
--   52,128-row scan, fetch_size unset -> 522 x FETCH 100   (100 is the documented default)
--
-- One value therefore has to serve both the LIMIT reads and the bulk range scans. Measured across
-- the range (525k-row seed):
--
--   fetch_size |  ORDER BY ts DESC LIMIT 100  |  52k-row range scan
--          200 |            3.3 ms            |       69.8 ms
--         1000 |            3.0 ms            |       62.4 ms
--         2000 |            3.4 ms            |       52.7 ms      <-- chosen
--        10000 |           17.5 ms            |       65.3 ms
--        50000 |           54.2 ms            |       55.7 ms
--
-- 2000 is at the bottom of both curves: bulk transfer has already flattened out (round trips stop
-- being the bottleneck above ~500), while a LIMIT read still only pulls 2000 rows instead of
-- 50000. A dedicated second mapping at fetch_size 200 would make the LIMIT read ~1.6 ms faster,
-- which is not worth a second foreign table and a second view over the same base table.
CREATE FOREIGN TABLE IF NOT EXISTS raw_sensor_reading (
    "timestamp" TIMESTAMPTZ NOT NULL,
    sensor_id TEXT NOT NULL,
    raw_value DOUBLE PRECISION NOT NULL,
    norm_value DOUBLE PRECISION,
    version SMALLINT,
    status TEXT
    )
    SERVER timescale_server
    OPTIONS (schema_name 'public', table_name 'sensor_reading', use_remote_estimate 'true', fetch_size '2000');
