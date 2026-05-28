CREATE SERVER timescale_server
    FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host '${fdw_ts_host}', port '${fdw_ts_port}', dbname '${fdw_ts_dbname}');

CREATE USER MAPPING IF NOT EXISTS FOR "${fdw_app_user}"
    SERVER timescale_server
    OPTIONS (user '${fdw_ts_user}', password '${fdw_ts_password}');


CREATE FOREIGN TABLE IF NOT EXISTS raw_simple_metrics (
    time  TIMESTAMPTZ      NOT NULL,
    value DOUBLE PRECISION
    )
    SERVER timescale_server
    OPTIONS (schema_name 'public', table_name 'simple_metrics');