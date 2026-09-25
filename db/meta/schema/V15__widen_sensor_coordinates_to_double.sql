-- -----------------------------------------------------------------------------
-- MON-142: widen sensors.x/y/z from INTEGER to DOUBLE PRECISION.
--
-- Coordinates are no longer typed by hand only: a sensor carrying a fulcrum_id takes its position
-- from Fulcrum's computed LV95 values (fx/fy/fz_point_with_offset), which are metres with three
-- decimals, e.g. 2579333.768. INTEGER columns silently truncated those to whole metres on write,
-- so a Fulcrum-linked sensor landed up to a metre away from where it is installed.
--
-- Widening is done in place: DOUBLE PRECISION covers every value INTEGER could hold, so existing
-- rows carry over unchanged and no rewrite of the domain's alarm limits (already DOUBLE PRECISION
-- since V2) or of the row level security policies is needed.
-- -----------------------------------------------------------------------------
ALTER TABLE sensors
    ALTER COLUMN x TYPE DOUBLE PRECISION,
    ALTER COLUMN y TYPE DOUBLE PRECISION,
    ALTER COLUMN z TYPE DOUBLE PRECISION;
