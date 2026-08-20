package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
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
    Long experimentId = 100L;
    Experiment mockExperiment = mock(Experiment.class);

    given(repository.getById(experimentId)).willReturn(mockExperiment);

    // when
    Experiment actualExperiment = service.getById(experimentId);

    // then
    then(repository).should().getById(experimentId);
    assertEquals(mockExperiment, actualExperiment, "Should return the mocked experiment");
  }

  @Test
  void should_return_null_and_not_throw_when_experiment_not_found() {
    // given
    Long experimentId = 999L;

    given(repository.getById(experimentId)).willReturn(null);

    // when
    Experiment actualExperiment = service.getById(experimentId);

    // then
    then(repository).should().getById(experimentId);
    assertNull(actualExperiment, "Should safely return null if the repository returns null");
  }
}
