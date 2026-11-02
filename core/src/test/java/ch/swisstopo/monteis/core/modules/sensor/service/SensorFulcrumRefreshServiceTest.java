package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAuthenticationException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.FulcrumRefreshResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor.Reason;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.exception.DataChangedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorFulcrumRefreshServiceTest {

  private static final Coordinates STORED = new Coordinates(2579000.0, 1247000.0, 500.0);
  private static final Coordinates MOVED = new Coordinates(2579333.768, 1247000.0, 500.0);

  @Mock private SensorRepository repository;
  @Mock private FulcrumService fulcrumService;
  @Mock private SensorService sensorService;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

  @InjectMocks private SensorFulcrumRefreshService service;

  @Test
  void should_save_changed_coordinates_and_only_stamp_unchanged_sensors() {
    // given
    Sensor moved = sensor(UUID.randomUUID());
    Sensor unchanged = sensor(UUID.randomUUID());
    given(repository.findAllByIds(Set.of(moved.getId(), unchanged.getId())))
        .willReturn(List.of(moved, unchanged));
    given(fulcrumService.getSensorsByIds(anyCollection()))
        .willReturn(List.of(fulcrumSensor(moved, MOVED), fulcrumSensor(unchanged, STORED)));

    // when
    FulcrumRefreshResult result = service.refresh(Set.of(moved.getId(), unchanged.getId()));

    // then
    assertEquals(new FulcrumRefreshResult(2, 1, List.of()), result);
    assertEquals(MOVED, moved.getCoordinates());
    assertEquals(OffsetDateTime.now(clock), moved.getLastFulcrumRefreshAt());
    then(sensorService).should().saveRefreshedCoordinates(moved);
    then(repository)
        .should()
        .markFulcrumRefreshed(List.of(unchanged.getId()), OffsetDateTime.now(clock));
  }

  @Test
  void should_skip_sensors_that_cannot_be_compared_and_refresh_the_rest() {
    // given
    UUID hidden = UUID.randomUUID();
    Sensor withoutFulcrumId = sensor(UUID.randomUUID());
    withoutFulcrumId.setFulcrumId(null);
    Sensor unknownToFulcrum = sensor(UUID.randomUUID());
    Sensor withoutHeight = sensor(UUID.randomUUID());
    Sensor moved = sensor(UUID.randomUUID());
    Set<UUID> ids =
        Set.of(
            hidden,
            withoutFulcrumId.getId(),
            unknownToFulcrum.getId(),
            withoutHeight.getId(),
            moved.getId());
    given(repository.findAllByIds(ids))
        .willReturn(List.of(withoutFulcrumId, unknownToFulcrum, withoutHeight, moved));
    given(fulcrumService.getSensorsByIds(anyCollection()))
        .willReturn(
            List.of(
                fulcrumSensor(withoutHeight, new Coordinates(2579000.0, 1247000.0, null)),
                fulcrumSensor(moved, MOVED)));

    // when
    FulcrumRefreshResult result = service.refresh(ids);

    // then
    assertEquals(1, result.checked());
    assertEquals(1, result.updated());
    assertEquals(
        Set.of(
            new SkippedSensor(hidden, Reason.NOT_FOUND),
            new SkippedSensor(withoutFulcrumId.getId(), Reason.NO_FULCRUM_ID),
            new SkippedSensor(unknownToFulcrum.getId(), Reason.NOT_IN_FULCRUM),
            new SkippedSensor(withoutHeight.getId(), Reason.COORDINATES_MISSING)),
        Set.copyOf(result.skipped()));
    assertEquals(STORED, withoutHeight.getCoordinates());
    then(sensorService).should().saveRefreshedCoordinates(moved);
  }

  @Test
  void should_skip_a_sensor_changed_meanwhile_and_save_the_others() {
    // given
    Sensor changedMeanwhile = sensor(UUID.randomUUID());
    Sensor moved = sensor(UUID.randomUUID());
    Set<UUID> ids = Set.of(changedMeanwhile.getId(), moved.getId());
    given(repository.findAllByIds(ids)).willReturn(List.of(changedMeanwhile, moved));
    given(fulcrumService.getSensorsByIds(anyCollection()))
        .willReturn(List.of(fulcrumSensor(changedMeanwhile, MOVED), fulcrumSensor(moved, MOVED)));
    willThrow(new DataChangedException("changed"))
        .given(sensorService)
        .saveRefreshedCoordinates(changedMeanwhile);

    // when
    FulcrumRefreshResult result = service.refresh(ids);

    // then
    assertEquals(
        new FulcrumRefreshResult(
            1, 1, List.of(new SkippedSensor(changedMeanwhile.getId(), Reason.CHANGED_MEANWHILE))),
        result);
    then(sensorService).should().saveRefreshedCoordinates(moved);
  }

  @Test
  void should_save_nothing_when_fulcrum_fails() {
    // given
    Sensor sensor = sensor(UUID.randomUUID());
    given(repository.findAllByIds(Set.of(sensor.getId()))).willReturn(List.of(sensor));
    given(fulcrumService.getSensorsByIds(anyCollection()))
        .willThrow(new FulcrumAuthenticationException("rejected"));

    // when / then
    assertThrows(
        FulcrumAuthenticationException.class, () -> service.refresh(Set.of(sensor.getId())));
    then(repository).should(never()).markFulcrumRefreshed(anyCollection(), any());
    then(sensorService).shouldHaveNoInteractions();
  }

  private static Sensor sensor(UUID id) {
    return new Sensor(
        id,
        "BSW04_PP_1",
        null,
        Das.SOL_EXPERTS,
        UUID.randomUUID(),
        null,
        STORED,
        true,
        null,
        1,
        List.of());
  }

  private static FulcrumSensor fulcrumSensor(Sensor sensor, Coordinates coordinates) {
    return new FulcrumSensor(
        sensor.getFulcrumId(),
        sensor.getName(),
        List.of("Sensor"),
        coordinates.x(),
        coordinates.y(),
        coordinates.z());
  }
}
