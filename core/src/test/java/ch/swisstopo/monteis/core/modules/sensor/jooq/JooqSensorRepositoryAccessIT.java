package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** The checks of {@link JooqSensorRepository} that row-level security answers. */
@IT
@Transactional
class JooqSensorRepositoryAccessIT {

  @Autowired private JooqSensorRepository repository;
  @Autowired private DSLContext dsl;

  private UUID alphaSensor;
  private UUID betaSensor;

  @BeforeEach
  void findSeededSensors() {
    alphaSensor = sensorOf(SeedData.EXPERIMENT_ALPHA);
    betaSensor = sensorOf(SeedData.EXPERIMENT_BETA);
  }

  @Test
  void should_let_a_writer_upload_documents_to_the_sensors_of_their_experiment_only() {
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA, SeedData.EXPERIMENT_BETA),
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> {
          assertThat(repository.canUploadDocumentsTo(alphaSensor)).isTrue();
          assertThat(repository.canUploadDocumentsTo(betaSensor)).isFalse();
        });
  }

  @Test
  void should_not_let_a_reader_upload_documents() {
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> assertThat(repository.canUploadDocumentsTo(alphaSensor)).isFalse());
  }

  @Test
  void should_let_admins_and_global_editors_upload_documents_to_every_sensor() {
    assertThat(callAsAdmin(() -> repository.canUploadDocumentsTo(betaSensor))).isTrue();
    SecurityContextTestSupport.runAsGlobalEditor(
        () -> assertThat(repository.canUploadDocumentsTo(betaSensor)).isTrue());
  }

  @Test
  void should_treat_a_hidden_sensor_like_an_unknown_one() {
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> {
          assertDoesNotThrow(() -> repository.requireVisible(alphaSensor));
          assertThrows(ObjectNotFoundException.class, () -> repository.requireVisible(betaSensor));
          assertThrows(
              ObjectNotFoundException.class, () -> repository.requireVisible(UUID.randomUUID()));
        });
  }

  // the seeded experiments share no sensors
  private UUID sensorOf(UUID experimentId) {
    return callAsAdmin(
        () ->
            dsl.select(EXPERIMENT_SENSOR.SENSOR_ID)
                .from(EXPERIMENT_SENSOR)
                .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(experimentId))
                .limit(1)
                .fetchSingle()
                .value1());
  }
}
