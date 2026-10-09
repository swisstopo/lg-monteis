package ch.swisstopo.monteis.core.modules.organisation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.domain.OrganisationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganisationServiceTest {

  @Mock private OrganisationRepository repository;
  @InjectMocks private OrganisationService service;

  @Test
  void should_create_through_the_repository() {
    Organisation toCreate = new Organisation("Swisstopo");
    Organisation created = new Organisation(UUID.randomUUID(), "Swisstopo");
    given(repository.create(toCreate)).willReturn(created);

    assertThat(service.createOrganisation(toCreate)).isEqualTo(created);
  }

  @Test
  void should_update_through_the_repository() {
    Organisation toUpdate = new Organisation(UUID.randomUUID(), "Renamed");
    given(repository.update(toUpdate)).willReturn(toUpdate);

    assertThat(service.updateOrganisation(toUpdate)).isEqualTo(toUpdate);
  }

  @Test
  void should_list_through_the_repository() {
    List<Organisation> all = List.of(new Organisation(UUID.randomUUID(), "Swisstopo"));
    given(repository.findAll()).willReturn(all);

    assertThat(service.findAllOrganisations()).isEqualTo(all);
  }

  @Test
  void should_get_an_organisation_by_id() {
    UUID id = UUID.randomUUID();
    Organisation organisation = new Organisation(id, "Swisstopo");
    given(repository.findById(id)).willReturn(Optional.of(organisation));

    assertThat(service.getOrganisation(id)).isEqualTo(organisation);
  }

  @Test
  void should_throw_not_found_for_an_unknown_id() {
    UUID id = UUID.randomUUID();
    given(repository.findById(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getOrganisation(id))
        .isInstanceOf(ObjectNotFoundException.class);
  }

  @Test
  void should_page_through_the_repository() {
    PagedRequest request = new PagedRequest(0, 10, List.of(), Map.of());
    PagedResult<Organisation> page =
        new PagedResult<>(List.of(new Organisation(UUID.randomUUID(), "Swisstopo")), 1);
    given(repository.getOrganisations(request)).willReturn(page);

    assertThat(service.getOrganisations(request)).isEqualTo(page);
  }
}
