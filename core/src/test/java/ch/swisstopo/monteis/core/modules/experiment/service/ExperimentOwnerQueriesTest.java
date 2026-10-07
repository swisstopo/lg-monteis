package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentOwnerQueriesTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final UUID OTHER_EXPERIMENT_ID = UUID.randomUUID();
  private static final DirectoryUser ALICE =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final DirectoryUser BOB =
      new DirectoryUser(UUID.randomUUID(), "Bob", "Builder", "bob@example.test");
  private static final DirectoryUser CAROL =
      new DirectoryUser(UUID.randomUUID(), "Carol", "Zimmer", "carol@example.test");

  @Mock private ExperimentOwnerQueryRepository ownerQueryRepository;
  @Mock private UserDirectory userDirectory;

  @InjectMocks private ExperimentOwnerQueries queries;

  @Nested
  class OwnersOf {

    @Test
    void should_show_only_owners_that_are_still_pis() {
      UUID leftTheGroup = UUID.randomUUID();
      given(userDirectory.pisForReading(EXPERIMENT_ID)).willReturn(known(ALICE, BOB));

      VisibleOwners owners = ownersOf(experiment(EXPERIMENT_ID, ALICE.id(), leftTheGroup));

      assertEquals(VisibleOwners.of(List.of(ALICE)), owners);
    }

    @Test
    void should_not_ask_keycloak_for_an_experiment_without_owners() {
      assertEquals(VisibleOwners.NONE, ownersOf(experiment(EXPERIMENT_ID)));
      then(userDirectory).shouldHaveNoInteractions();
    }

    @Test
    void should_hide_owners_without_a_write_group() {
      given(userDirectory.pisForReading(EXPERIMENT_ID)).willReturn(new Pis.NoWriteGroup());

      assertEquals(VisibleOwners.NO_WRITE_GROUP, ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }

    @Test
    void should_hide_owners_when_keycloak_denies_reading_them() {
      given(userDirectory.pisForReading(EXPERIMENT_ID)).willReturn(new Pis.AccessDenied("403"));

      assertEquals(VisibleOwners.ACCESS_DENIED, ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }

    @Test
    void should_mark_owners_unavailable_when_keycloak_is_unavailable() {
      given(userDirectory.pisForReading(EXPERIMENT_ID))
          .willReturn(new Pis.KeycloakUnavailable("down"));

      assertEquals(
          VisibleOwners.KEYCLOAK_UNAVAILABLE, ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }
  }

  @Nested
  class OwnersByExperiment {

    @Test
    void should_resolve_owners_per_experiment_in_order() {
      given(userDirectory.pisForReading(EXPERIMENT_ID)).willReturn(known(ALICE));
      given(userDirectory.pisForReading(OTHER_EXPERIMENT_ID)).willReturn(known(BOB));

      List<ExperimentWithOwners> owners =
          queries.withOwners(
              List.of(
                  experiment(EXPERIMENT_ID, ALICE.id()),
                  experiment(OTHER_EXPERIMENT_ID, BOB.id())));

      assertEquals(List.of(ALICE), owners.get(0).owners().users());
      assertEquals(List.of(BOB), owners.get(1).owners().users());
    }

    @Test
    void should_stop_asking_keycloak_once_it_is_unavailable() {
      given(userDirectory.pisForReading(EXPERIMENT_ID))
          .willReturn(new Pis.KeycloakUnavailable("down"));

      List<ExperimentWithOwners> owners =
          queries.withOwners(
              List.of(
                  experiment(EXPERIMENT_ID, ALICE.id()),
                  experiment(OTHER_EXPERIMENT_ID, BOB.id())));

      assertEquals(VisibleOwners.KEYCLOAK_UNAVAILABLE, owners.get(1).owners());
      then(userDirectory).should(never()).pisForReading(OTHER_EXPERIMENT_ID);
    }
  }

  @Test
  void should_list_every_filterable_owner_once_sorted_by_name() {
    given(ownerQueryRepository.findStoredOwners())
        .willReturn(
            List.of(
                new StoredOwners(EXPERIMENT_ID, Set.of(ALICE.id(), CAROL.id())),
                new StoredOwners(OTHER_EXPERIMENT_ID, Set.of(ALICE.id(), BOB.id()))));
    given(userDirectory.pisForReading(EXPERIMENT_ID)).willReturn(known(ALICE, CAROL));
    given(userDirectory.pisForReading(OTHER_EXPERIMENT_ID)).willReturn(known(ALICE, BOB));

    assertEquals(List.of(BOB, ALICE, CAROL), queries.filterableOwners());
  }

  private static Pis known(DirectoryUser... pis) {
    return new Pis.Known(List.of(pis));
  }

  private static Experiment experiment(UUID id, UUID... ownerIds) {
    return new Experiment(
        id,
        "EXP",
        new Period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
        null,
        1,
        0,
        Set.of(ownerIds));
  }

  private VisibleOwners ownersOf(Experiment experiment) {
    return queries.withOwners(experiment).owners();
  }
}
