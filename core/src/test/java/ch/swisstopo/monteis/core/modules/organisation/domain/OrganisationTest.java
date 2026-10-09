package ch.swisstopo.monteis.core.modules.organisation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganisationTest {

  @Test
  void should_trim_and_collapse_whitespace_in_the_name() {
    Organisation organisation = new Organisation(UUID.randomUUID(), "  Swiss   Topo ", null);

    assertThat(organisation.getName()).isEqualTo("Swiss Topo");
  }

  @Test
  void should_keep_the_case_of_the_name() {
    assertThat(new Organisation("ETH Zürich", null).getName()).isEqualTo("ETH Zürich");
  }

  @Test
  void should_reject_a_blank_name() {
    assertThatThrownBy(() -> new Organisation("  ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_missing_name() {
    assertThatThrownBy(() -> new Organisation(null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
