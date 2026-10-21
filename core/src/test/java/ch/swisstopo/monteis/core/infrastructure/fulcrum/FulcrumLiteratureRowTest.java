package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FulcrumLiteratureRowTest {

  private static final UUID FIRST = UUID.randomUUID();
  private static final UUID SECOND = UUID.randomUUID();

  @Test
  void should_take_the_first_attachment_with_its_name() {
    // given
    FulcrumLiteratureRow row =
        row(List.of(FIRST, SECOND), List.of("TR2010-01 (3).pdf", "Seeska, Lux 10 2015.pdf"));

    // when / then
    assertThat(row.firstAttachment())
        .contains(new FulcrumAttachmentReference(FIRST, "TR2010-01 (3).pdf"));
    assertThat(row.hasSeveralAttachments()).isTrue();
  }

  @Test
  void should_name_an_attachment_without_a_name_by_its_id() {
    // given
    FulcrumLiteratureRow row = row(List.of(FIRST), List.of());

    // when / then
    assertThat(row.firstAttachment())
        .contains(new FulcrumAttachmentReference(FIRST, FIRST.toString()));
    assertThat(row.hasSeveralAttachments()).isFalse();
  }

  @Test
  void should_have_no_attachment_and_no_keywords_when_fulcrum_sends_null() {
    // given
    FulcrumLiteratureRow row = row(null, null);

    // when / then
    assertThat(row.firstAttachment()).isEmpty();
    assertThat(row.hasSeveralAttachments()).isFalse();
    assertThat(row.keywords()).isEmpty();
  }

  private static FulcrumLiteratureRow row(List<UUID> ids, List<String> names) {
    return new FulcrumLiteratureRow(
        UUID.randomUUID(), "2011-02", "title", "Jahns E.", 2011, "category", null, ids, names);
  }
}
