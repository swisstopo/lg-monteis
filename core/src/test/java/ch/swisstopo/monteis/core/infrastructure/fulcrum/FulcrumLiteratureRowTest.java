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
  void should_pair_the_attachment_ids_with_their_names() {
    // given
    FulcrumLiteratureRow row =
        row(List.of(FIRST, SECOND), List.of("TR2010-01 (3).pdf", "Seeska, Lux 10 2015.pdf"));

    // when / then
    assertThat(row.attachments())
        .containsExactly(
            new FulcrumAttachmentReference(FIRST, "TR2010-01 (3).pdf"),
            new FulcrumAttachmentReference(SECOND, "Seeska, Lux 10 2015.pdf"));
  }

  @Test
  void should_name_an_attachment_without_a_name_by_its_id() {
    // given
    FulcrumLiteratureRow row = row(List.of(FIRST, SECOND), List.of("first.pdf"));

    // when / then
    assertThat(row.attachments())
        .containsExactly(
            new FulcrumAttachmentReference(FIRST, "first.pdf"),
            new FulcrumAttachmentReference(SECOND, SECOND.toString()));
  }

  @Test
  void should_have_no_attachments_and_no_keywords_when_fulcrum_sends_null() {
    // given
    FulcrumLiteratureRow row = row(null, null);

    // when / then
    assertThat(row.attachments()).isEmpty();
    assertThat(row.keywords()).isEmpty();
  }

  private static FulcrumLiteratureRow row(List<UUID> ids, List<String> names) {
    return new FulcrumLiteratureRow(
        UUID.randomUUID(), "2011-02", "title", "Jahns E.", 2011, "category", null, ids, names);
  }
}
