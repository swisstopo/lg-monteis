-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth —
-- edits and removals here are reflected on the next run, not just additions.

TRUNCATE TABLE device, customer RESTART IDENTITY CASCADE;

INSERT INTO customer (name, email)
VALUES
    ('Alice Example', 'alice@example.com'),
    ('Bob Example',   'bob@example.com');

INSERT INTO device (customer_id, external_id)
SELECT c.id, d.external_id
FROM (VALUES
          ('alice@example.com', 'dev-device-001'),
          ('bob@example.com',   'dev-device-002')
     ) AS d(email, external_id)
         JOIN customer c ON c.email = d.email;