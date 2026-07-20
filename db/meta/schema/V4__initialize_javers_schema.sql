CREATE SEQUENCE jv_commit_pk_seq CACHE 100;
CREATE SEQUENCE jv_global_id_pk_seq CACHE 100;
CREATE SEQUENCE jv_snapshot_pk_seq CACHE 100;

CREATE TABLE jv_global_id
(
    global_id_pk BIGINT NOT NULL,
    local_id     VARCHAR(191),
    fragment     VARCHAR(200),
    type_name    VARCHAR(200),
    owner_id_fk  BIGINT,
    CONSTRAINT jv_global_id_pk PRIMARY KEY (global_id_pk),
    CONSTRAINT jv_global_id_owner_id_fk FOREIGN KEY (owner_id_fk) REFERENCES jv_global_id (global_id_pk)
);

CREATE TABLE jv_commit
(
    commit_pk           BIGINT NOT NULL,
    author              VARCHAR(200),
    commit_date         TIMESTAMP,
    commit_date_instant VARCHAR(30),
    commit_id           NUMERIC(22, 2),
    CONSTRAINT jv_commit_pk PRIMARY KEY (commit_pk)
);

CREATE TABLE jv_commit_property
(
    commit_fk      BIGINT       NOT NULL,
    property_name  VARCHAR(191) NOT NULL,
    property_value VARCHAR(600),
    CONSTRAINT jv_commit_property_pk PRIMARY KEY (commit_fk, property_name),
    CONSTRAINT jv_commit_property_commit_fk FOREIGN KEY (commit_fk) REFERENCES jv_commit (commit_pk)
);

CREATE TABLE jv_snapshot
(
    snapshot_pk        BIGINT NOT NULL,
    type               VARCHAR(200),
    version            BIGINT,
    state              TEXT,
    changed_properties VARCHAR(2000),
    managed_type       VARCHAR(200),
    commit_fk          BIGINT,
    global_id_fk       BIGINT,
    CONSTRAINT jv_snapshot_pk PRIMARY KEY (snapshot_pk),
    CONSTRAINT jv_snapshot_commit_fk FOREIGN KEY (commit_fk) REFERENCES jv_commit (commit_pk),
    CONSTRAINT jv_snapshot_global_id_fk FOREIGN KEY (global_id_fk) REFERENCES jv_global_id (global_id_pk)
);