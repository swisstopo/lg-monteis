package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentOwnerServiceTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final UUID OTHER_EXPERIMENT_ID = UUID.randomUUID();
  private static final DirectoryUser ALICE =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final DirectoryUser BOB =
      new DirectoryUser(UUID.randomUUID(), "Bob", "Builder", "bob@example.test");
  private static final DirectoryUser CAROL =
      new DirectoryUser(UUID.randomUUID(), "Carol", "Zimmer", "carol@example.test");

  @Mock private ExperimentRepository repository;
  @Mock private ExperimentOwnerQueryRepository ownerQueryRepository;
  @Mock private UserDirectory userDirectory;

  @InjectMocks private ExperimentOwnerService service;

  @Nested
  class OwnersOf {

    @Test
    void should_show_only_owners_that_are_still_pis() {
      UUID leftTheGroup = UUID.randomUUID();
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE, BOB));

      VisibleOwners owners = service.ownersOf(experiment(EXPERIMENT_ID, ALICE.id(), leftTheGroup));

      assertEquals(VisibleOwners.of(List.of(ALICE)), owners);
    }

    @Test
    void should_not_ask_keycloak_for_an_experiment_without_owners() {
      assertEquals(VisibleOwners.NONE, service.ownersOf(experiment(EXPERIMENT_ID)));
      then(userDirectory).shouldHaveNoInteractions();
    }

    @Test
    void should_hide_owners_without_a_write_group() {
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.NoWriteGroup());

      assertEquals(VisibleOwners.NONE, service.ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }

    @Test
    void should_hide_owners_when_keycloak_denies_reading_them() {
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.AccessDenied("403"));

      assertEquals(VisibleOwners.NONE, service.ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }

    @Test
    void should_mark_owners_unavailable_when_keycloak_is_unavailable() {
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.KeycloakUnavailable("down"));

      assertEquals(
          VisibleOwners.UNAVAILABLE, service.ownersOf(experiment(EXPERIMENT_ID, ALICE.id())));
    }
  }

  @Nested
  class OwnersByExperiment {

    @Test
    void should_resolve_owners_per_experiment_in_order() {
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE));
      given(userDirectory.principalInvestigatorsOf(OTHER_EXPERIMENT_ID)).willReturn(known(BOB));

      Map<UUID, VisibleOwners> owners =
          service.ownersByExperiment(
              List.of(
                  experiment(EXPERIMENT_ID, ALICE.id()),
                  experiment(OTHER_EXPERIMENT_ID, BOB.id())));

      assertEquals(List.of(EXPERIMENT_ID, OTHER_EXPERIMENT_ID), List.copyOf(owners.keySet()));
      assertEquals(List.of(ALICE), owners.get(EXPERIMENT_ID).users());
      assertEquals(List.of(BOB), owners.get(OTHER_EXPERIMENT_ID).users());
    }

    @Test
    void should_stop_asking_keycloak_once_it_is_unavailable() {
      given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.KeycloakUnavailable("down"));

      Map<UUID, VisibleOwners> owners =
          service.ownersByExperiment(
              List.of(
                  experiment(EXPERIMENT_ID, ALICE.id()),
                  experiment(OTHER_EXPERIMENT_ID, BOB.id())));

      assertEquals(VisibleOwners.UNAVAILABLE, owners.get(OTHER_EXPERIMENT_ID));
      then(userDirectory).should(never()).principalInvestigatorsOf(OTHER_EXPERIMENT_ID);
    }
  }

  @Test
  void should_list_every_filterable_owner_once_sorted_by_name() {
    given(ownerQueryRepository.findStoredOwners())
        .willReturn(
            List.of(
                new StoredOwners(EXPERIMENT_ID, Set.of(ALICE.id(), CAROL.id())),
                new StoredOwners(OTHER_EXPERIMENT_ID, Set.of(ALICE.id(), BOB.id()))));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE, CAROL));
    given(userDirectory.principalInvestigatorsOf(OTHER_EXPERIMENT_ID))
        .willReturn(known(ALICE, BOB));

    assertEquals(List.of(BOB, ALICE, CAROL), service.filterableOwners());
  }

  @Nested
  class OwnerCandidates {

    @Test
    void should_check_the_experiment_is_readable_first() {
      willThrow(new ObjectNotFoundException(Experiment.class))
          .given(repository)
          .requireVisible(EXPERIMENT_ID);

      assertThrows(ObjectNotFoundException.class, () -> service.ownerCandidates(EXPERIMENT_ID));
      then(userDirectory).shouldHaveNoInteractions();
    }

    @Test
    void should_ask_keycloak_fresh() {
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE));

      assertEquals(List.of(ALICE), service.ownerCandidates(EXPERIMENT_ID));
      then(userDirectory).should(never()).principalInvestigatorsOf(any());
    }

    @Test
    void should_have_none_without_a_write_group() {
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.NoWriteGroup());

      assertEquals(List.of(), service.ownerCandidates(EXPERIMENT_ID));
    }

    @Test
    void should_fail_apart_when_keycloak_denies_or_is_unavailable() {
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(
              new PrincipalInvestigators.AccessDenied("403"),
              new PrincipalInvestigators.KeycloakUnavailable("down"));

      assertThrows(
          UserDirectoryDeniedException.class, () -> service.ownerCandidates(EXPERIMENT_ID));
      assertThrows(
          UserDirectoryUnavailableException.class, () -> service.ownerCandidates(EXPERIMENT_ID));
    }
  }

  @Nested
  class ReplaceOwners {

    @Test
    void should_replace_owners_with_pis_of_the_experiment() {
      Experiment replaced = experiment(EXPERIMENT_ID, ALICE.id());
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(known(ALICE, BOB));
      given(repository.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id()))).willReturn(replaced);

      assertSame(replaced, service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id())));
    }

    @Test
    void should_reject_an_owner_that_is_not_a_pi_of_the_experiment() {
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE));

      FieldBusinessValidationException e =
          assertThrows(
              FieldBusinessValidationException.class,
              () -> service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id(), BOB.id())));

      assertEquals(ExperimentOwnerService.OWNER_IDS_FIELD, e.getField());
      assertEquals(ExperimentOwnerService.NOT_ELIGIBLE_KEY, e.getMessageKey());
      assertEquals(Set.of(BOB.id()), e.getActualValue());
      then(repository).should(never()).replaceOwners(any(), any());
    }

    @Test
    void should_allow_clearing_the_owners_without_a_write_group() {
      Experiment cleared = experiment(EXPERIMENT_ID);
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.NoWriteGroup());
      given(repository.replaceOwners(EXPERIMENT_ID, Set.of())).willReturn(cleared);

      assertSame(cleared, service.replaceOwners(EXPERIMENT_ID, Set.of()));
    }

    @Test
    void should_not_replace_owners_when_keycloak_is_unavailable() {
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(new PrincipalInvestigators.KeycloakUnavailable("down"));

      assertThrows(
          UserDirectoryUnavailableException.class,
          () -> service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id())));
      then(repository).should(never()).replaceOwners(any(), any());
    }
  }

  @Nested
  class DropFormerOwners {

    @Test
    void should_drop_owners_that_are_no_longer_pis() {
      UUID leftTheGroup = UUID.randomUUID();
      Experiment pruned = experiment(EXPERIMENT_ID, ALICE.id());
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(known(ALICE, BOB));
      given(repository.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id()))).willReturn(pruned);

      assertSame(
          pruned, service.dropFormerOwners(experiment(EXPERIMENT_ID, ALICE.id(), leftTheGroup)));
    }

    @Test
    void should_not_write_when_every_owner_is_still_a_pi() {
      Experiment saved = experiment(EXPERIMENT_ID, ALICE.id());
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(known(ALICE, BOB));

      assertSame(saved, service.dropFormerOwners(saved));
      then(repository).shouldHaveNoInteractions();
    }

    @Test
    void should_keep_every_owner_without_a_complete_answer_from_keycloak() {
      Experiment saved = experiment(EXPERIMENT_ID, ALICE.id());
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID))
          .willReturn(
              new PrincipalInvestigators.NoWriteGroup(),
              new PrincipalInvestigators.AccessDenied("403"),
              new PrincipalInvestigators.KeycloakUnavailable("down"));

      for (int i = 0; i < 3; i++) {
        assertSame(saved, service.dropFormerOwners(saved));
      }
      then(repository).shouldHaveNoInteractions();
    }

    @Test
    void should_keep_the_owners_when_writing_fails() {
      Experiment saved = experiment(EXPERIMENT_ID, UUID.randomUUID());
      given(userDirectory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID)).willReturn(known(ALICE));
      given(repository.replaceOwners(EXPERIMENT_ID, Set.of()))
          .willThrow(new IllegalStateException("db down"));

      assertSame(saved, service.dropFormerOwners(saved));
    }

    @Test
    void should_not_ask_keycloak_for_an_experiment_without_owners() {
      Experiment saved = experiment(EXPERIMENT_ID);

      assertSame(saved, service.dropFormerOwners(saved));
      then(userDirectory).shouldHaveNoInteractions();
    }
  }

  private static PrincipalInvestigators known(DirectoryUser... pis) {
    return new PrincipalInvestigators.Known(List.of(pis));
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
}
