package ch.swisstopo.monteis.core.infrastructure.api;

public final class ApiPaths {

  public static final String EXPERIMENT_ID = "id";

  public static final String EXPERIMENT_ID_SEGMENT = "{" + EXPERIMENT_ID + "}";

  public static final String EXPERIMENTS = "/api/experiments";
  public static final String EXPERIMENT = EXPERIMENTS + "/" + EXPERIMENT_ID_SEGMENT;
  public static final String EXPERIMENT_DOCUMENTS = EXPERIMENT + "/documents";
  public static final String EXPERIMENT_OWNERS = EXPERIMENT + "/owners";
  public static final String EXPERIMENT_OWNER_CANDIDATES = EXPERIMENT + "/owner-candidates";

  public static final String ALL_EXPERIMENT_OWNERS = EXPERIMENTS + "/owners";

  public static final String SENSORS = "/api/sensors";

  private ApiPaths() {}
}
