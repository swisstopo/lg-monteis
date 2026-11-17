package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAs;
import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotificationParameterResponseDto;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// seed: Alpha = TEMP-1 (401), PRESS-1&2 (402), 10 bulk sensors without readings (bulk-sensor-1 with
// 2 parameters); Beta = PRESS-1&2 (402), DISP-2 (403), FLOW-2 (404)
@IT
class NotificationParameterQueryRepositoryIT {

  private static final UUID TEMP_1_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000401");
  private static final UUID PRESS_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000402");
  private static final UUID DISP_2_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000403");
  private static final UUID FLOW_2_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000404");
  private static final UUID PRESS_SENSOR = UUID.fromString("00000000-0000-7000-8000-000000000202");

  @Autowired private NotificationParameterQueryRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  void should_list_every_parameter_of_the_experiment_while_the_flag_is_not_readable() {
    // when
    List<NotificationParameterResponseDto> parameters =
        callAsAdmin(() -> repository.findNotificationParametersOf(SeedData.EXPERIMENT_BETA));

    // then
    assertThat(parameters)
        .extracting(NotificationParameterResponseDto::sensorParameterId)
        .containsExactly(PRESS_PARAM, DISP_2_PARAM, FLOW_2_PARAM);
  }

  @Test
  void should_list_a_shared_sensor_on_every_experiment_it_is_linked_to() {
    // when
    List<UUID> alpha =
        callAsAdmin(
            () -> idsOf(repository.findNotificationParametersOf(SeedData.EXPERIMENT_ALPHA)));
    List<UUID> beta =
        callAsAdmin(() -> idsOf(repository.findNotificationParametersOf(SeedData.EXPERIMENT_BETA)));

    // then
    assertThat(alpha).contains(PRESS_PARAM, TEMP_1_PARAM).doesNotContain(DISP_2_PARAM);
    assertThat(beta).contains(PRESS_PARAM).doesNotContain(TEMP_1_PARAM);
  }

  @Test
  void should_order_by_sensor_name_then_parameter_name() {
    // when
    List<NotificationParameterResponseDto> parameters =
        callAsAdmin(() -> repository.findNotificationParametersOf(SeedData.EXPERIMENT_ALPHA));

    // then
    assertThat(parameters).hasSize(13);
    assertThat(parameters.subList(0, 2))
        .extracting(
            NotificationParameterResponseDto::sensorName,
            NotificationParameterResponseDto::sensorParameterName)
        .containsExactly(
            tuple("bulk-sensor-1", "Bulk Param"), tuple("bulk-sensor-1", "Bulk Param 2"));
    assertThat(parameters.getLast().sensorName()).isEqualTo("monteis-002");
  }

  @Test
  void should_list_a_parameter_without_a_reading_with_an_empty_last_measurement() {
    // when
    NotificationParameterResponseDto bulk =
        callAsAdmin(() -> repository.findNotificationParametersOf(SeedData.EXPERIMENT_ALPHA))
            .getFirst();

    // then
    assertThat(bulk.sensorName()).isEqualTo("bulk-sensor-1");
    assertThat(bulk.dasKey()).isEqualTo("SOL_EXPERTS__BULK-1__BULK-P1");
    assertThat(bulk.unit()).isEqualTo("METER");
    assertThat(bulk.newestMeasurement()).isNull();
    assertThat(bulk.measureValue()).isNull();
    assertThat(bulk.measurementStatus()).isNull();
  }

  @Test
  @Transactional
  void should_carry_the_newest_reading_and_its_measurement_state() {
    // given
    var newest =
        callAsAdmin(
            () ->
                dsl.select(
                        SENSOR_READING_SECURED.TIMESTAMP,
                        SENSOR_READING_SECURED.NORM_VALUE,
                        SENSOR_READING_SECURED.STATUS)
                    .from(SENSOR_READING_SECURED)
                    .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(TEMP_1_PARAM))
                    .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
                    .limit(1)
                    .fetchSingle());

    // when
    NotificationParameterResponseDto temperature =
        callAsAdmin(() -> findIn(SeedData.EXPERIMENT_ALPHA, TEMP_1_PARAM));

    // then
    assertThat(temperature.sensorName()).isEqualTo("monteis-001");
    assertThat(temperature.sensorParameterName()).isEqualTo("Temperature Param");
    assertThat(temperature.dasKey()).isEqualTo("SOL_EXPERTS__TEMP-1__temperature");
    assertThat(temperature.unit()).isEqualTo("KELVIN");
    assertThat(temperature.newestMeasurement()).isEqualTo(newest.value1());
    assertThat(temperature.measureValue()).isEqualTo(newest.value2());
    assertThat(temperature.measurementStatus())
        .isEqualTo(MeasurementStatus.fromDbValue(newest.value3()));
  }

  @Test
  @Transactional
  void should_carry_the_active_flags_of_sensor_and_parameter() {
    // given
    callAsAdmin(
        () -> {
          dsl.update(SENSOR_PARAMETER)
              .set(SENSOR_PARAMETER.ACTIVE, false)
              .where(SENSOR_PARAMETER.ID.eq(TEMP_1_PARAM))
              .execute();
          return dsl.update(SENSORS)
              .set(SENSORS.ACTIVE, false)
              .where(SENSORS.ID.eq(PRESS_SENSOR))
              .execute();
        });

    // when
    NotificationParameterResponseDto temperature =
        callAsAdmin(() -> findIn(SeedData.EXPERIMENT_ALPHA, TEMP_1_PARAM));
    NotificationParameterResponseDto pressure =
        callAsAdmin(() -> findIn(SeedData.EXPERIMENT_ALPHA, PRESS_PARAM));

    // then
    assertThat(temperature.sensorActive()).isTrue();
    assertThat(temperature.sensorParameterActive()).isFalse();
    assertThat(pressure.sensorActive()).isFalse();
    assertThat(pressure.sensorParameterActive()).isTrue();
  }

  @Test
  void should_list_the_parameters_for_a_pi_of_the_experiment() {
    // when
    List<NotificationParameterResponseDto> parameters =
        callAs(
            PrivilegeLevel.EXPERIMENT_PI.authentication(),
            () -> repository.findNotificationParametersOf(PrivilegeLevel.ASSIGNED_EXPERIMENT));

    // then
    assertThat(parameters).hasSize(13);
  }

  private NotificationParameterResponseDto findIn(UUID experimentId, UUID sensorParameterId) {
    return repository.findNotificationParametersOf(experimentId).stream()
        .filter(parameter -> parameter.sensorParameterId().equals(sensorParameterId))
        .findFirst()
        .orElseThrow();
  }

  private static List<UUID> idsOf(List<NotificationParameterResponseDto> parameters) {
    return parameters.stream().map(NotificationParameterResponseDto::sensorParameterId).toList();
  }
}
