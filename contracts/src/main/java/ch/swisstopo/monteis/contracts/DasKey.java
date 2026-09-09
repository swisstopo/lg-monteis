package ch.swisstopo.monteis.contracts;

/**
 * Builds the composite DAS ingest-routing key ("&lt;DAS&gt;__&lt;das_sensor_alias&gt;__&lt;das_parameter_alias&gt;"),
 * used as the Kafka message key on {@code internal-sensor-config} and all downstream sensor-data topics, and as
 * the lookup key in the pipeline's {@code SensorConfigCache}. Kept here, shared between core (publisher,
 * normalizer key-building) and pipeline (normalizer, cache), so both sides always agree on the exact format.
 */
public final class DasKey {

  private static final String SEPARATOR = "__";

  private DasKey() {}

  public static String compose(Das das, String dasSensorAlias, String dasParameterAlias) {
    return das.getValue() + SEPARATOR + dasSensorAlias + SEPARATOR + dasParameterAlias;
  }
}
