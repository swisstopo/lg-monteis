package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentSnapshotBackfillServiceTest {
  @Mock private ExperimentRepository repository;

  @Mock private Javers javers;

  @InjectMocks private ExperimentSnapshotBackfillService service;

  @Test
  void should_stream_unaudited_experiments_and_commit_to_javers() {
    // given
    Experiment experiment1 = mock(Experiment.class);
    Experiment experiment2 = mock(Experiment.class);

    Stream<Experiment> mockStream = Stream.of(experiment1, experiment2);

    given(repository.streamUnauditedExperiments()).willReturn(mockStream);

    // when
    service.backfillMissingSnapshots();

    // then
    then(repository).should().streamUnauditedExperiments();
    then(javers).should().commit("SYSTEM_SEEDER", experiment1);
    then(javers).should().commit("SYSTEM_SEEDER", experiment2);
  }
}
