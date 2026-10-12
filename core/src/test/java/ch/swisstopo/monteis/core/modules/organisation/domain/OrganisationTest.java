package ch.swisstopo.monteis.core.modules.organisation.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganisationTest {

  @Test
  void should_initialize_new_organisation_without_id() {
    // when
    Organisation organisation = new Organisation("Swisstopo", "A comment");

    // then
    assertAll(
        () -> assertEquals("Swisstopo", organisation.getName(), "Name should be mapped correctly"),
        () ->
            assertEquals(
                "A comment", organisation.getComment(), "Comment should be mapped correctly"),
        () ->
            assertNull(organisation.getId(), "ID should be null for a newly created organisation"));
  }

  @Test
  void should_rebuild_existing_organisation_from_the_database() {
    // given
    UUID id = UUID.randomUUID();

    // when
    Organisation organisation = new Organisation(id, "ETH Zürich", null);

    // then
    assertAll(
        () -> assertEquals(id, organisation.getId(), "ID should be mapped correctly"),
        () -> assertEquals("ETH Zürich", organisation.getName(), "Name should be kept as is"),
        () -> assertNull(organisation.getComment(), "Missing comment should stay null"));
  }
}
