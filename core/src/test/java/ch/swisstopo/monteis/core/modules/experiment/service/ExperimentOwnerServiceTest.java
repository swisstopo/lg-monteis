package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

  @Test
  void should_show_only_owners_that_are_still_pis_of_the_experiment() {
    UUID leftTheGroup = UUID.randomUUID();
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE, BOB));

    VisibleOwners owners =
        service.visibleOwners(experiment(EXPERIMENT_ID, Set.of(ALICE.id(), leftTheGroup)));

    assertEquals(new VisibleOwners(List.of(ALICE), false), owners);
  }

  @Test
  void should_not_ask_keycloak_for_an_experiment_without_owners() {
    VisibleOwners owners = service.visibleOwners(experiment(EXPERIMENT_ID, Set.of()));

    assertEquals(VisibleOwners.NONE, owners);
    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_mark_owners_unavailable_instead_of_failing_when_keycloak_is_unavailable() {
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryUnavailableException("down", null));

    VisibleOwners owners = service.visibleOwners(experiment(EXPERIMENT_ID, Set.of(ALICE.id())));

    assertEquals(VisibleOwners.UNAVAILABLE, owners);
  }

  @Test
  void should_hide_owners_when_keycloak_denies_reading_them() {
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryAccessDeniedException("forbidden", null));

    VisibleOwners owners = service.visibleOwners(experiment(EXPERIMENT_ID, Set.of(ALICE.id())));

    assertEquals(VisibleOwners.NONE, owners);
  }

  @Test
  void should_resolve_owners_per_experiment_in_order() {
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE));
    given(userDirectory.principalInvestigatorsOf(OTHER_EXPERIMENT_ID)).willReturn(List.of(BOB));

    Map<UUID, VisibleOwners> owners =
        service.visibleOwners(
            List.of(
                experiment(EXPERIMENT_ID, Set.of(ALICE.id())),
                experiment(OTHER_EXPERIMENT_ID, Set.of(BOB.id()))));

    assertEquals(List.of(EXPERIMENT_ID, OTHER_EXPERIMENT_ID), List.copyOf(owners.keySet()));
    assertEquals(List.of(ALICE), owners.get(EXPERIMENT_ID).users());
    assertEquals(List.of(BOB), owners.get(OTHER_EXPERIMENT_ID).users());
  }

  @Test
  void should_list_every_assigned_owner_once_sorted_by_name() {
    given(ownerQueryRepository.findOwnerIdsByExperiment())
        .willReturn(
            Map.of(
                EXPERIMENT_ID, Set.of(ALICE.id(), CAROL.id()),
                OTHER_EXPERIMENT_ID, Set.of(ALICE.id(), BOB.id())));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE, CAROL));
    given(userDirectory.principalInvestigatorsOf(OTHER_EXPERIMENT_ID))
        .willReturn(List.of(ALICE, BOB));

    assertEquals(List.of(BOB, ALICE, CAROL), service.assignedOwners());
  }

  @Test
  void should_check_the_experiment_is_readable_before_listing_candidates() {
    given(repository.getById(EXPERIMENT_ID))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    assertThrows(ObjectNotFoundException.class, () -> service.candidates(EXPERIMENT_ID));
    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_replace_owners_with_pis_of_the_experiment() {
    Experiment replaced = experiment(EXPERIMENT_ID, Set.of(ALICE.id()));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE, BOB));
    given(repository.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id()))).willReturn(replaced);

    assertSame(replaced, service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id())));
  }

  @Test
  void should_reject_an_owner_that_is_not_a_pi_of_the_experiment() {
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE));

    FieldBusinessValidationException e =
        assertThrows(
            FieldBusinessValidationException.class,
            () -> service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id(), BOB.id())));

    assertEquals(ExperimentOwnerService.OWNER_IDS_FIELD, e.getField());
    assertEquals(ExperimentOwnerService.NOT_ELIGIBLE_KEY, e.getMessageKey());
    assertEquals(Set.of(BOB.id()), e.getActualValue());
    then(repository).should().getById(EXPERIMENT_ID);
    then(repository).shouldHaveNoMoreInteractions();
  }

  @Test
  void should_not_replace_owners_when_keycloak_is_unavailable() {
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryUnavailableException("down", null));

    assertThrows(
        UserDirectoryUnavailableException.class,
        () -> service.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id())));
    then(repository).should().getById(EXPERIMENT_ID);
    then(repository).should(never()).replaceOwners(any(), any());
  }

  @Test
  void should_drop_owners_that_are_no_longer_pis() {
    UUID leftTheGroup = UUID.randomUUID();
    Experiment saved = experiment(EXPERIMENT_ID, Set.of(ALICE.id(), leftTheGroup));
    Experiment pruned = experiment(EXPERIMENT_ID, Set.of(ALICE.id()));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE, BOB));
    given(repository.replaceOwners(EXPERIMENT_ID, Set.of(ALICE.id()))).willReturn(pruned);

    assertSame(pruned, service.dropFormerOwners(saved));
  }

  @Test
  void should_not_write_when_every_owner_is_still_a_pi() {
    Experiment saved = experiment(EXPERIMENT_ID, Set.of(ALICE.id()));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID)).willReturn(List.of(ALICE, BOB));

    assertSame(saved, service.dropFormerOwners(saved));
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void should_keep_the_owners_when_keycloak_is_unavailable() {
    Experiment saved = experiment(EXPERIMENT_ID, Set.of(ALICE.id()));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryUnavailableException("down", null));

    assertSame(saved, service.dropFormerOwners(saved));
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void should_keep_the_owners_when_keycloak_denies_reading_the_pis() {
    Experiment saved = experiment(EXPERIMENT_ID, Set.of(ALICE.id()));
    given(userDirectory.principalInvestigatorsOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryAccessDeniedException("forbidden", null));

    assertSame(saved, service.dropFormerOwners(saved));
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void should_not_ask_keycloak_when_dropping_owners_of_an_experiment_without_owners() {
    Experiment saved = experiment(EXPERIMENT_ID, Set.of());

    assertSame(saved, service.dropFormerOwners(saved));
    then(userDirectory).shouldHaveNoInteractions();
  }

  private static Experiment experiment(UUID id, Set<UUID> ownerIds) {
    return new Experiment(
        id,
        "EXP",
        new Period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
        null,
        1,
        0,
        ownerIds);
  }
}
