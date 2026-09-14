package ch.swisstopo.monteis.core.itconfig;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.FulcrumStub;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the e2e Fulcrum stub through the same generated client the application uses.
 *
 * <p>The stub only ever runs inside the Playwright suite, where a mistake in it shows up as a
 * confusing {@code object.deleted} on screen rather than as a failure pointing at the stub. It
 * already cost one debugging round: the statement reaches the stub percent-encoded, so matching
 * the {@code _record_id} filter against the raw query parameter silently found nothing and every
 * save looked like a deleted Fulcrum record. Going through {@link DefaultApi} rather than calling
 * the request-parsing directly is the point of this test - the encoding is done by the generated
 * client, so only a real request proves the two halves still fit.
 */
class FulcrumStubConfigurationTest {

  private static final String TABLE = "Mont Terri Monitoring Systems and Sensors";

  private static FulcrumStub stub;
  private static FulcrumService service;

  @BeforeAll
  static void startStub() throws IOException {
    stub = new FulcrumStubConfiguration().fulcrumStub();

    FulcrumProperties properties =
        new FulcrumProperties(
            stub.baseUrl(),
            "test-token",
            TABLE,
            20000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5));
    DefaultApi api =
        HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(RestClient.builder().baseUrl(stub.baseUrl()).build()))
            .build()
            .createClient(DefaultApi.class);

    service = new FulcrumService(api, properties, JsonMapper.builder().build());
  }

  @AfterAll
  static void stopStub() {
    stub.close();
  }

  @Test
  void answers_any_record_id_with_a_record_carrying_that_id() {
    UUID recordId = UUID.randomUUID();

    Optional<FulcrumSensor> sensor = service.getSensorById(recordId);

    assertThat(sensor).isPresent();
    assertThat(sensor.get().recordId()).isEqualTo(recordId);
    assertThat(sensor.get().isSensor()).isTrue();
  }

  @Test
  void answers_with_the_coordinates_the_playwright_tests_assert_on() {
    FulcrumSensor sensor = service.getSensorById(UUID.randomUUID()).orElseThrow();

    assertThat(sensor.xPointWithOffset()).isEqualTo(FulcrumStubConfiguration.STUB_X);
    assertThat(sensor.yPointWithOffset()).isEqualTo(FulcrumStubConfiguration.STUB_Y);
    assertThat(sensor.zPointWithOffset()).isEqualTo(FulcrumStubConfiguration.STUB_Z);
  }

  @Test
  void reports_the_reserved_record_id_as_missing() {
    Optional<FulcrumSensor> sensor =
        service.getSensorById(FulcrumStubConfiguration.UNKNOWN_RECORD_ID);

    assertThat(sensor).isEmpty();
  }

  @Test
  void has_nothing_to_answer_an_unfiltered_query_with() {
    List<FulcrumSensor> sensors = service.getSensors();

    assertThat(sensors)
        .as("the stub answers per record id only; a full table read has no id to echo back")
        .isEmpty();
  }
}
