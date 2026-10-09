package ch.swisstopo.monteis.core.modules.organisation.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IT
@Transactional
class JooqOrganisationRepositoryIT {

  @Autowired private JooqOrganisationRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  void should_create_an_organisation_with_a_generated_id() {
    Organisation created = repository.create(new Organisation("Swisstopo IT"));

    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo("Swisstopo IT");
  }

  @Test
  void should_reject_a_name_that_exists_ignoring_case() {
    repository.create(new Organisation("ETH Zürich IT"));

    assertThatThrownBy(() -> repository.create(new Organisation("eth zürich it")))
        .isInstanceOf(FieldBusinessValidationException.class);
  }

  @Test
  void should_rename_an_organisation() {
    Organisation created = repository.create(new Organisation("Before rename IT"));

    Organisation updated = repository.update(new Organisation(created.getId(), "After rename IT"));

    assertThat(updated.getId()).isEqualTo(created.getId());
    assertThat(repository.findById(created.getId()).orElseThrow().getName())
        .isEqualTo("After rename IT");
  }

  @Test
  void should_reject_a_rename_to_a_name_that_exists_ignoring_case() {
    repository.create(new Organisation("Taken IT"));
    Organisation other = repository.create(new Organisation("Other IT"));

    assertThatThrownBy(() -> repository.update(new Organisation(other.getId(), "taken it")))
        .isInstanceOf(FieldBusinessValidationException.class);
  }

  @Test
  void should_answer_not_found_when_updating_an_unknown_organisation() {
    assertThatThrownBy(() -> repository.update(new Organisation(UUID.randomUUID(), "Nobody IT")))
        .isInstanceOf(ObjectNotFoundException.class);
  }

  @Test
  void should_list_organisations_sorted_by_name() {
    repository.create(new Organisation("ZZZ sort IT"));
    repository.create(new Organisation("AAA sort IT"));

    var names = repository.findAll().stream().map(Organisation::getName).toList();

    assertThat(names).containsSubsequence("AAA sort IT", "ZZZ sort IT");
  }

  @Test
  void should_page_filter_and_sort_organisations() {
    repository.create(new Organisation("Page IT B"));
    repository.create(new Organisation("Page IT A"));
    repository.create(new Organisation("Page IT C"));

    var request =
        new PagedRequest(
            0,
            2,
            List.of(new SortModelItem("name", SortDirection.DESC)),
            Map.of("name", new TextFilterModel("contains", "Page IT", null)));

    var page = repository.getOrganisations(request);

    assertThat(page.totalCount()).isEqualTo(3);
    assertThat(page.rows())
        .extracting(Organisation::getName)
        .containsExactly("Page IT C", "Page IT B");
  }

  @Test
  void should_find_an_organisation_by_id() {
    Organisation created = repository.create(new Organisation("Find me IT"));

    assertThat(repository.findById(created.getId()))
        .hasValueSatisfying(found -> assertThat(found.getName()).isEqualTo("Find me IT"));
  }

  @Test
  void should_return_empty_for_an_unknown_id() {
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
  }
}
