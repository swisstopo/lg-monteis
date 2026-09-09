package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Envelope returned by the Fulcrum Query API for {@code format=json}.
 *
 * <p>Hand-written, unlike the rest of the Fulcrum models: the spec types this response as
 * {@code EmptySuccessResponse}, an object with no properties, so there is nothing to generate.
 * FulcrumSpecTest asserts that this is still the case and will fail the day Fulcrum documents it.
 *
 * <p>Only {@code rows} carries payload. {@code fields} (column name/type pairs, sent only with
 * {@code metadata=true}) and the {@code time}/{@code date} timing values are deliberately not
 * mapped - callers get plain row objects instead of having to walk the envelope.
 *
 * @param <T> type the rows are deserialized into, e.g. {@link FulcrumSensor}
 */
public record FulcrumQueryResponse<T>(@JsonProperty("rows") List<T> rows) {

  @Override
  public List<T> rows() {
    return rows == null ? List.of() : rows;
  }
}
