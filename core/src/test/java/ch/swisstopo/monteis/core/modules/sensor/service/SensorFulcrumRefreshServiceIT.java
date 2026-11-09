package ch.swisstopo.monteis.core.modules.sensor.service;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAs;
import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.FulcrumStub;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.QueryFailure;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.StubSensor;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.FulcrumRefreshResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor.Reason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.repository.jql.QueryBuilder;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// not @Transactional: the refresh commits one sensor at a time
@IT
@Import(FulcrumStubConfiguration.class)
@TestPropertySource(properties = {"monteis.fulcrum.enabled=true", "monteis.fulcrum.per-page=10"})
class SensorFulcrumRefreshServiceIT {

  // = monteis.fulcrum.per-page
  private static final int FULCRUM_PAGE_SIZE = 10;
  private static final UUID PI_SUBJECT = UUID.randomUUID();
  private static final Coordinates STORED = new Coordinates(2579000.0, 1247000.0, 500.0);
  private static final Coordinates IN_FULCRUM = new Coordinates(2579321.5, 1247865.25, 512.0);

  @Autowired private SensorFulcrumRefreshService service;
  @MockitoSpyBean private SensorRepository sensorRepository;
  @Autowired private ExperimentRepository experimentRepository;
  @Autowired private FulcrumStub fulcrum;
  @Autowired private Javers javers;
  @Autowired private DSLContext dsl;
  @Autowired private PlatformTransactionManager transactionManager;

  private final List<UUID> createdSensorIds = new ArrayList<>();
  private Experiment writtenByPi;
  private Experiment readByPi;
  private Experiment hiddenFromPi;

  @BeforeEach
  void createTheExperiments() {
    writtenByPi = createExperiment();
    readByPi = createExperiment();
    hiddenFromPi = createExperiment();
  }

  @AfterEach
  void removeTheSensors() {
    fulcrum.resetQueries();
    inTransactionAsAdmin(
        () -> {
          dsl.deleteFrom(SENSOR_PARAMETER)
              .where(SENSOR_PARAMETER.SENSOR_ID.in(createdSensorIds))
              .execute();
          dsl.deleteFrom(EXPERIMENT_SENSOR)
              .where(EXPERIMENT_SENSOR.SENSOR_ID.in(createdSensorIds))
              .execute();
          return dsl.deleteFrom(SENSORS).where(SENSORS.ID.in(createdSensorIds)).execute();
        });
  }

  @Test
  void should_take_changed_coordinates_over_and_audit_them_under_the_refreshing_user() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(writtenByPi, fulcrumId, STORED);
    fulcrum.serveSensors(inFulcrum(fulcrumId, IN_FULCRUM));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(result).isEqualTo(new FulcrumRefreshResult(1, 1, List.of()));
    Sensor stored = storedSensor(sensorId);
    assertThat(stored.getCoordinates()).isEqualTo(IN_FULCRUM);
    assertThat(stored.getLastFulcrumRefreshAt()).isNotNull();
    assertThat(snapshotsOf(sensorId))
        .singleElement()
        .extracting(snapshot -> snapshot.getCommitMetadata().getAuthor())
        .isEqualTo(PI_SUBJECT.toString());
  }

  @Test
  void should_record_the_last_fulcrum_refresh_of_an_unchanged_sensor_without_an_audit_entry() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(writtenByPi, fulcrumId, IN_FULCRUM);
    int versionBefore = storedSensor(sensorId).getVersion();
    fulcrum.serveSensors(inFulcrum(fulcrumId, IN_FULCRUM));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(result).isEqualTo(new FulcrumRefreshResult(1, 0, List.of()));
    Sensor stored = storedSensor(sensorId);
    assertThat(stored.getLastFulcrumRefreshAt()).isNotNull();
    assertThat(stored.getVersion()).isEqualTo(versionBefore);
    assertThat(snapshotsOf(sensorId)).isEmpty();
  }

  @Test
  void should_leave_out_a_selected_sensor_without_fulcrum_id_silently() {
    // given
    UUID sensorId = createSensor(writtenByPi, null, STORED);

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(result).isEqualTo(new FulcrumRefreshResult(0, 0, List.of()));
    assertThat(storedSensor(sensorId).getLastFulcrumRefreshAt()).isNull();
  }

  @Test
  void should_report_sensors_missing_or_incomplete_in_fulcrum_with_their_names() {
    // given
    UUID notInFulcrum = createSensor(writtenByPi, UUID.randomUUID(), STORED);
    UUID withoutHeightFulcrumId = UUID.randomUUID();
    UUID withoutHeight = createSensor(writtenByPi, withoutHeightFulcrumId, STORED);
    fulcrum.serveSensors(
        new StubSensor(withoutHeightFulcrumId, IN_FULCRUM.x(), IN_FULCRUM.y(), null));

    // when
    FulcrumRefreshResult result =
        asPi(() -> service.refreshSelected(Set.of(notInFulcrum, withoutHeight)));

    // then
    assertThat(result.checked()).isZero();
    assertThat(result.updated()).isZero();
    assertThat(result.skipped())
        .containsExactlyInAnyOrder(
            new SkippedSensor(notInFulcrum, nameOf(notInFulcrum), Reason.NOT_IN_FULCRUM),
            new SkippedSensor(withoutHeight, nameOf(withoutHeight), Reason.COORDINATES_MISSING));
    assertThat(storedSensor(withoutHeight).getCoordinates()).isEqualTo(STORED);
    assertThat(storedSensor(withoutHeight).getLastFulcrumRefreshAt()).isNull();
  }

  @Test
  void should_report_unknown_and_hidden_sensors_as_not_found_without_their_names() {
    // given
    UUID unknown = UUID.randomUUID();
    UUID hiddenFulcrumId = UUID.randomUUID();
    UUID hidden = createSensor(hiddenFromPi, hiddenFulcrumId, STORED);
    fulcrum.serveSensors(inFulcrum(hiddenFulcrumId, IN_FULCRUM));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(unknown, hidden)));

    // then
    assertThat(result.skipped())
        .containsExactlyInAnyOrder(
            new SkippedSensor(unknown, null, Reason.NOT_FOUND),
            new SkippedSensor(hidden, null, Reason.NOT_FOUND));
    assertThat(storedSensor(hidden).getCoordinates()).isEqualTo(STORED);
  }

  @Test
  void should_let_a_pi_refresh_a_sensor_of_an_experiment_they_only_read() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(readByPi, fulcrumId, STORED);
    fulcrum.serveSensors(inFulcrum(fulcrumId, IN_FULCRUM));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(result).isEqualTo(new FulcrumRefreshResult(1, 1, List.of()));
    assertThat(storedSensor(sensorId).getCoordinates()).isEqualTo(IN_FULCRUM);
  }

  @Test
  void should_refresh_every_visible_sensor_when_none_are_selected() {
    // given
    UUID changedFulcrumId = UUID.randomUUID();
    UUID changed = createSensor(writtenByPi, changedFulcrumId, STORED);
    UUID unchangedFulcrumId = UUID.randomUUID();
    UUID unchanged = createSensor(readByPi, unchangedFulcrumId, IN_FULCRUM);
    UUID withoutFulcrumId = createSensor(writtenByPi, null, STORED);
    UUID hiddenFulcrumId = UUID.randomUUID();
    UUID hidden = createSensor(hiddenFromPi, hiddenFulcrumId, STORED);
    fulcrum.serveSensors(
        inFulcrum(changedFulcrumId, IN_FULCRUM),
        inFulcrum(unchangedFulcrumId, IN_FULCRUM),
        inFulcrum(hiddenFulcrumId, IN_FULCRUM));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshAllVisible());

    // then
    assertThat(result).isEqualTo(new FulcrumRefreshResult(2, 1, List.of()));
    assertThat(storedSensor(changed).getCoordinates()).isEqualTo(IN_FULCRUM);
    assertThat(storedSensor(unchanged).getLastFulcrumRefreshAt()).isNotNull();
    assertThat(storedSensor(withoutFulcrumId).getLastFulcrumRefreshAt()).isNull();
    assertThat(storedSensor(hidden).getCoordinates()).isEqualTo(STORED);
    assertThat(storedSensor(hidden).getLastFulcrumRefreshAt()).isNull();
  }

  @Test
  void should_report_a_sensor_saved_meanwhile_as_changed_meanwhile_and_keep_that_save() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(writtenByPi, fulcrumId, STORED);
    fulcrum.serveSensors(inFulcrum(fulcrumId, IN_FULCRUM));
    fulcrum.whileQueried(() -> saveCommentMeanwhile(sensorId));

    // when
    FulcrumRefreshResult result = asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(result)
        .isEqualTo(
            new FulcrumRefreshResult(
                0,
                0,
                List.of(new SkippedSensor(sensorId, nameOf(sensorId), Reason.CHANGED_MEANWHILE))));
    Sensor stored = storedSensor(sensorId);
    assertThat(stored.getCoordinates()).isEqualTo(STORED);
    assertThat(stored.getComment()).isEqualTo("saved meanwhile");
  }

  @Test
  void should_not_record_the_last_fulcrum_refresh_once_the_fulcrum_id_was_removed_meanwhile() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(writtenByPi, fulcrumId, IN_FULCRUM);
    fulcrum.serveSensors(inFulcrum(fulcrumId, IN_FULCRUM));
    fulcrum.whileQueried(() -> removeFulcrumIdMeanwhile(sensorId));

    // when
    asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    Sensor stored = storedSensor(sensorId);
    assertThat(stored.getFulcrumId()).isNull();
    assertThat(stored.getLastFulcrumRefreshAt()).isNull();
  }

  @Test
  void should_take_the_first_of_duplicate_fulcrum_records() {
    // given
    UUID fulcrumId = UUID.randomUUID();
    UUID sensorId = createSensor(writtenByPi, fulcrumId, STORED);
    fulcrum.serveSensors(
        inFulcrum(fulcrumId, IN_FULCRUM), inFulcrum(fulcrumId, new Coordinates(1.0, 2.0, 3.0)));

    // when
    asPi(() -> service.refreshSelected(Set.of(sensorId)));

    // then
    assertThat(storedSensor(sensorId).getCoordinates()).isEqualTo(IN_FULCRUM);
  }

  @Test
  void should_keep_the_sensors_saved_before_a_save_fails() {
    // given
    UUID firstFulcrumId = UUID.randomUUID();
    UUID first = createSensor(writtenByPi, firstFulcrumId, STORED);
    UUID secondFulcrumId = UUID.randomUUID();
    UUID second = createSensor(writtenByPi, secondFulcrumId, STORED);
    UUID unchangedFulcrumId = UUID.randomUUID();
    UUID unchanged = createSensor(writtenByPi, unchangedFulcrumId, IN_FULCRUM);
    fulcrum.serveSensors(
        inFulcrum(firstFulcrumId, IN_FULCRUM),
        inFulcrum(secondFulcrumId, IN_FULCRUM),
        inFulcrum(unchangedFulcrumId, IN_FULCRUM));
    doCallRealMethod()
        .doThrow(new DataAccessResourceFailureException("connection lost"))
        .when(sensorRepository)
        .updateRefreshedCoordinates(any());
    Set<UUID> selected = Set.of(first, second, unchanged);

    // when
    assertThrows(
        DataAccessResourceFailureException.class,
        () -> asPi(() -> service.refreshSelected(selected)));

    // then
    // save order follows the db read: either sensor may be the one saved before the failure
    assertThat(Stream.of(first, second).map(id -> storedSensor(id).getCoordinates()))
        .containsExactlyInAnyOrder(IN_FULCRUM, STORED);
    assertThat(storedSensor(unchanged).getLastFulcrumRefreshAt()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = QueryFailure.class,
      names = {"UNREACHABLE", "TOKEN_REJECTED"})
  void should_write_nothing_when_fulcrum_cannot_be_read(QueryFailure failure) {
    // given
    UUID changedFulcrumId = UUID.randomUUID();
    UUID changed = createSensor(writtenByPi, changedFulcrumId, STORED);
    UUID unchangedFulcrumId = UUID.randomUUID();
    UUID unchanged = createSensor(writtenByPi, unchangedFulcrumId, IN_FULCRUM);
    fulcrum.serveSensors(
        inFulcrum(changedFulcrumId, IN_FULCRUM), inFulcrum(unchangedFulcrumId, IN_FULCRUM));
    fulcrum.failQueriesWith(failure);
    Set<UUID> selected = Set.of(changed, unchanged);

    // when
    assertThrows(RuntimeException.class, () -> asPi(() -> service.refreshSelected(selected)));

    // then
    assertThat(storedSensor(changed).getCoordinates()).isEqualTo(STORED);
    assertThat(storedSensor(changed).getLastFulcrumRefreshAt()).isNull();
    assertThat(storedSensor(unchanged).getLastFulcrumRefreshAt()).isNull();
  }

  @Test
  void should_fail_and_write_nothing_when_fulcrum_returns_a_full_page() {
    // given
    List<UUID> fulcrumIds = Stream.generate(UUID::randomUUID).limit(FULCRUM_PAGE_SIZE).toList();
    Set<UUID> selected =
        fulcrumIds.stream()
            .map(fulcrumId -> createSensor(writtenByPi, fulcrumId, STORED))
            .collect(Collectors.toSet());
    fulcrum.serveSensors(
        fulcrumIds.stream()
            .map(fulcrumId -> inFulcrum(fulcrumId, IN_FULCRUM))
            .toArray(StubSensor[]::new));

    // when
    assertThrows(IllegalStateException.class, () -> asPi(() -> service.refreshSelected(selected)));

    // then
    assertThat(selected)
        .allSatisfy(
            sensorId -> {
              assertThat(storedSensor(sensorId).getCoordinates()).isEqualTo(STORED);
              assertThat(storedSensor(sensorId).getLastFulcrumRefreshAt()).isNull();
            });
  }

  private Experiment createExperiment() {
    Experiment experiment =
        new Experiment(
            "REFRESH-IT-" + UUID.randomUUID().toString().substring(0, 8),
            new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            null);
    return callAsAdmin(() -> experimentRepository.create(experiment));
  }

  private UUID createSensor(
      Experiment experiment, @Nullable UUID fulcrumId, Coordinates coordinates) {
    String alias = "REFRESH-IT-" + UUID.randomUUID().toString().substring(0, 8);
    Sensor sensor =
        new Sensor(
            "Sensor " + alias,
            alias,
            Das.SOL_EXPERTS,
            fulcrumId,
            experiment,
            coordinates,
            true,
            null);
    UUID id = callAsAdmin(() -> sensorRepository.create(sensor)).getId();
    createdSensorIds.add(id);
    return id;
  }

  private static StubSensor inFulcrum(UUID fulcrumId, Coordinates coordinates) {
    return new StubSensor(fulcrumId, coordinates.x(), coordinates.y(), coordinates.z());
  }

  // another user's save: new version, the refresh's read is stale
  private void saveCommentMeanwhile(UUID sensorId) {
    inTransactionAsAdmin(
        () ->
            dsl.update(SENSORS)
                .set(SENSORS.COMMENT, "saved meanwhile")
                .set(SENSORS.VERSION, SENSORS.VERSION.plus(1))
                .where(SENSORS.ID.eq(sensorId))
                .execute());
  }

  // what the sensor save does when an admin removes the fulcrum id
  private void removeFulcrumIdMeanwhile(UUID sensorId) {
    inTransactionAsAdmin(
        () ->
            dsl.update(SENSORS)
                .setNull(SENSORS.FULCRUM_ID)
                .setNull(SENSORS.LAST_FULCRUM_REFRESH_AT)
                .set(SENSORS.VERSION, SENSORS.VERSION.plus(1))
                .where(SENSORS.ID.eq(sensorId))
                .execute());
  }

  private <T> T asPi(Supplier<T> action) {
    MonteisPrincipal pi =
        new MonteisPrincipal(
            PI_SUBJECT,
            "pi",
            List.of(writtenByPi.getId(), readByPi.getId()),
            List.of(writtenByPi.getId()));
    return callAs(
        new MonteisAuthenticationToken(null, pi, PrivilegeLevel.EXPERIMENT_PI.grantedAuthorities()),
        action);
  }

  private Sensor storedSensor(UUID sensorId) {
    return callAsAdmin(() -> sensorRepository.findById(sensorId)).orElseThrow();
  }

  private String nameOf(UUID sensorId) {
    return storedSensor(sensorId).getName();
  }

  private List<CdoSnapshot> snapshotsOf(UUID sensorId) {
    return javers.findSnapshots(QueryBuilder.byInstanceId(sensorId, Sensor.class).build());
  }

  // RLS fails closed outside a transaction and would hide every row
  private <T> T inTransactionAsAdmin(Supplier<T> statement) {
    return callAsAdmin(
        () -> new TransactionTemplate(transactionManager).execute(status -> statement.get()));
  }
}
