package ch.swisstopo.monteis.core.infrastructure.api;

/**
 * API paths shared by the controllers and {@code SecurityConfig}. With a literal on each side a
 * renamed path or path variable would silently drop out of its security rule.
 */
public final class ApiPaths {

  public static final String EXPERIMENT_ID = "id";

  /**
   * The experiment id as a path segment, for mappings in a controller mapped to {@link
   * #EXPERIMENTS}.
   */
  public static final String EXPERIMENT_ID_SEGMENT = "{" + EXPERIMENT_ID + "}";

  public static final String EXPERIMENTS = "/api/experiments";
  public static final String EXPERIMENT = EXPERIMENTS + "/" + EXPERIMENT_ID_SEGMENT;
  public static final String EXPERIMENT_DOCUMENTS = EXPERIMENT + "/documents";
  public static final String EXPERIMENT_OWNERS = EXPERIMENT + "/owners";
  public static final String EXPERIMENT_OWNER_CANDIDATES = EXPERIMENT + "/owner-candidates";

  /** The owners of all readable experiments at once, for the owner filter. */
  public static final String ALL_EXPERIMENT_OWNERS = EXPERIMENTS + "/owners";

  public static final String SENSORS = "/api/sensors";

  private ApiPaths() {}
}
