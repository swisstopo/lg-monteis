package ch.swisstopo.monteis.core.infrastructure.api;

/** Paths the controllers map and {@code SecurityConfig} guards, so both name the same path. */
public final class ApiPaths {

  public static final String EXPERIMENT_ID = "id";

  /** The experiment id segment, for mappings below {@link #EXPERIMENTS}. */
  public static final String EXPERIMENT_ID_SEGMENT = "{" + EXPERIMENT_ID + "}";

  public static final String EXPERIMENTS = "/api/experiments";
  public static final String EXPERIMENT = EXPERIMENTS + "/" + EXPERIMENT_ID_SEGMENT;
  public static final String EXPERIMENT_DOCUMENTS = EXPERIMENT + "/documents";
  public static final String SENSORS = "/api/sensors";

  private ApiPaths() {}
}
