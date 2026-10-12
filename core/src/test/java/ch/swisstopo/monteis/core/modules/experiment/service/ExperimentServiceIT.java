package ch.swisstopo.monteis.core.modules.experiment.service;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.javers.core.Javers;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.repository.jql.QueryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// not @Transactional, the update and its rollback have to commit like in production
@IT
class ExperimentServiceIT {

  private static final DirectoryUser ALICE =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final DirectoryUser BOB =
      new DirectoryUser(UUID.randomUUID(), "Bob", "Builder", "bob@example.test");

  @Autowired private ExperimentService service;
  @Autowired private Javers javers;
  @MockitoBean private UserDirectory userDirectory;

  @Test
  void should_audit_the_master_data_and_the_owners_in_one_snapshot() {
    // given
    Experiment created = callAsAdmin(() -> service.createExperiment(newExperiment()));
    given(userDirectory.pisForWriting(created.getId())).willReturn(new Pis.Known(List.of(ALICE)));
    created.setComment("changed with the owners");

    // when
    Experiment updated =
        callAsAdmin(() -> service.updateExperimentAndOwners(created, Set.of(ALICE.id())));

    // then
    assertThat(updated.getOwnerIds()).containsExactly(ALICE.id());
    assertThat(updated.getComment()).isEqualTo("changed with the owners");
    CdoSnapshot latest = latestSnapshotOf(created.getId());
    assertThat(latest.getChanged()).contains("comment", "ownerIds").doesNotContain("sensorCount");
  }

  @Test
  void should_roll_back_the_master_data_when_an_owner_is_no_pi() {
    // given
    Experiment created = callAsAdmin(() -> service.createExperiment(newExperiment()));
    given(userDirectory.pisForWriting(created.getId())).willReturn(new Pis.Known(List.of(BOB)));
    String storedComment = created.getComment();
    created.setComment("must not be saved");

    // when
    assertThrows(
        FieldBusinessValidationException.class,
        () -> callAsAdmin(() -> service.updateExperimentAndOwners(created, Set.of(ALICE.id()))));

    // then
    Experiment reloaded = callAsAdmin(() -> service.getById(created.getId()));
    assertThat(reloaded.getComment()).isEqualTo(storedComment);
    assertThat(reloaded.getOwnerIds()).isEmpty();
  }

  private CdoSnapshot latestSnapshotOf(UUID experimentId) {
    return javers
        .findSnapshots(QueryBuilder.byInstanceId(experimentId, Experiment.class).limit(1).build())
        .getFirst();
  }

  private static Experiment newExperiment() {
    return new Experiment(
        "OWNER-IT-" + UUID.randomUUID().toString().substring(0, 8),
        new Period(LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
        "stored comment");
  }
}
