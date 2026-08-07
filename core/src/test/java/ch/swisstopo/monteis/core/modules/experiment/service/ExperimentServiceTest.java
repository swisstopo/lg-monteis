package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
