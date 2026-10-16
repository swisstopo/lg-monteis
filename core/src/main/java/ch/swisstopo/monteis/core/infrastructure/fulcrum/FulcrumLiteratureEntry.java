package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

// hand-written like FulcrumSensor, Fulcrum leaves absent values as null so every component is
// nullable
public record FulcrumLiteratureEntry(
    @JsonProperty("_record_id") UUID recordId,
    @JsonProperty("number") @Nullable String number,
    @JsonProperty("reference_title") @Nullable String title,
    @JsonProperty("author_sequence") @Nullable String authors,
    @JsonProperty("year") @Nullable Integer year,
    @JsonProperty("categories") @Nullable String category,
    @JsonProperty("keywords") @Nullable List<String> keywords,
    @JsonProperty("document") @Nullable List<UUID> attachmentIds,
    @JsonProperty("document_names") @Nullable List<String> attachmentNames) {

  public List<String> keywords() {
    return keywords == null ? List.of() : keywords;
  }

  // document and document_names are parallel arrays
  public List<FulcrumAttachmentReference> attachments() {
    if (attachmentIds == null) {
      return List.of();
    }
    return IntStream.range(0, attachmentIds.size())
        .mapToObj(i -> new FulcrumAttachmentReference(attachmentIds.get(i), nameAt(i)))
        .toList();
  }

  // a name missing from the parallel array would otherwise drop the file, the id still downloads
  private String nameAt(int index) {
    return attachmentNames != null && index < attachmentNames.size()
        ? attachmentNames.get(index)
        : attachmentIds.get(index).toString();
  }

  public record FulcrumAttachmentReference(UUID id, String name) {}
}
