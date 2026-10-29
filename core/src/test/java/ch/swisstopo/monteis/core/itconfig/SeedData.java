package ch.swisstopo.monteis.core.itconfig;

import java.util.UUID;

/** Fixed ids of db/meta/seed/R__seed_dev_data.sql, which the ITs run against. */
public final class SeedData {

  /** "Mont Terri Alpha", 12 sensors. */
  public static final UUID EXPERIMENT_ALPHA =
      UUID.fromString("00000000-0000-7000-8000-000000000301");

  /** "Mont Terri Beta", 3 sensors. */
  public static final UUID EXPERIMENT_BETA =
      UUID.fromString("00000000-0000-7000-8000-000000000302");

  /** "Mont Terri Gamma", no sensors. */
  public static final UUID EXPERIMENT_GAMMA =
      UUID.fromString("00000000-0000-7000-8000-000000000303");

  /** "TEMP-1", linked to Alpha only. */
  public static final UUID SENSOR_ONLY_IN_ALPHA =
      UUID.fromString("00000000-0000-7000-8000-000000000201");

  /** "DISP-2", linked to Beta only. "PRESS-1&2" is linked to both. */
  public static final UUID SENSOR_ONLY_IN_BETA =
      UUID.fromString("00000000-0000-7000-8000-000000000203");

  private SeedData() {}
}
