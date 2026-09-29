package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {
  @Mock private ExperimentRepository repository;

  @InjectMocks private ExperimentService service;

  @Test
  void should_delegate_create_experiment_to_repository() {
    // given
    Experiment inputExperiment = mock(Experiment.class);
    Experiment expectedExperiment = mock(Experiment.class);

    given(repository.create(inputExperiment)).willReturn(expectedExperiment);

    // when
    Experiment actualExperiment = service.createExperiment(inputExperiment);

    // then
    then(repository).should().create(inputExperiment);
    assertEquals(expectedExperiment, actualExperiment);
  }

  @Test
  void should_delegate_update_experiment_to_repository() {
    // given
    Experiment inputExperiment = mock(Experiment.class);
    Experiment expectedExperiment = mock(Experiment.class);

    given(repository.update(inputExperiment)).willReturn(expectedExperiment);

    // when
    Experiment actualExperiment = service.updateExperiment(inputExperiment);

    // then
    then(repository).should().update(inputExperiment);
    assertEquals(expectedExperiment, actualExperiment);
  }

  @Test
  void should_get_experiment_by_id_and_calculate_status_when_found() {
    // given
    UUID experimentId = UUID.randomUUID();
    Experiment mockExperiment = mock(Experiment.class);

    given(repository.getById(experimentId)).willReturn(mockExperiment);

    // when
    Experiment actualExperiment = service.getById(experimentId);

    // then
    then(repository).should().getById(experimentId);
    assertEquals(mockExperiment, actualExperiment, "Should return the mocked experiment");
  }

  @Test
  void should_propagate_not_found_when_the_experiment_is_missing_or_hidden() {
    // given
    UUID experimentId = UUID.randomUUID();
    ObjectNotFoundException notFound = new ObjectNotFoundException(Experiment.JAVERS_TYPE);

    given(repository.getById(experimentId)).willThrow(notFound);

    // when / then
    assertSame(
        notFound, assertThrows(ObjectNotFoundException.class, () -> service.getById(experimentId)));
    then(repository).should().getById(experimentId);
  }

  @Test
  void should_propagate_not_found_when_updating_a_missing_or_hidden_experiment() {
    // given
    Experiment inputExperiment = mock(Experiment.class);

    given(repository.update(inputExperiment))
        .willThrow(new ObjectNotFoundException(Experiment.JAVERS_TYPE));

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.updateExperiment(inputExperiment));
  }

  @Test
  void should_delegate_find_all_experiments_to_repository() {
    // given
    List<Experiment> expectedExperiments = List.of(mock(Experiment.class), mock(Experiment.class));

    given(repository.findAll()).willReturn(expectedExperiments);

    // when
    List<Experiment> actualExperiments = service.findAllExperiments();

    // then
    then(repository).should().findAll();
    assertEquals(expectedExperiments, actualExperiments);
  }
}
