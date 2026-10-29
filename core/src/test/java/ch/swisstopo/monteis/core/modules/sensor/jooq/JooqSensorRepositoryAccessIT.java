package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** The checks of {@link JooqSensorRepository} that row-level security answers. */
@IT
@Transactional
class JooqSensorRepositoryAccessIT {

  @Autowired private JooqSensorRepository repository;

  private final UUID alphaSensor = SeedData.SENSOR_ONLY_IN_ALPHA;
  private final UUID betaSensor = SeedData.SENSOR_ONLY_IN_BETA;

  @Test
  void should_let_a_writer_upload_documents_to_the_sensors_of_their_experiment_only() {
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA, SeedData.EXPERIMENT_BETA),
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> {
          assertThat(repository.canWriteAnExperimentOf(alphaSensor)).isTrue();
          assertThat(repository.canWriteAnExperimentOf(betaSensor)).isFalse();
        });
  }

  @Test
  void should_not_let_a_reader_upload_documents() {
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> assertThat(repository.canWriteAnExperimentOf(alphaSensor)).isFalse());
  }

  @Test
  void should_let_admins_and_global_editors_upload_documents_to_every_sensor() {
    assertThat(callAsAdmin(() -> repository.canWriteAnExperimentOf(betaSensor))).isTrue();
    SecurityContextTestSupport.runAsGlobalEditor(
        () -> assertThat(repository.canWriteAnExperimentOf(betaSensor)).isTrue());
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
}
