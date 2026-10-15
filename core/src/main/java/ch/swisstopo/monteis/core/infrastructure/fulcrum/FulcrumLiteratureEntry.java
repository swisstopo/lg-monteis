package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

// hand-written like FulcrumSensor, Fulcrum leaves absent values as null so every component is
// nullable
public record FulcrumLiteratureEntry(
    @JsonProperty("_record_id") UUID recordId,
    @JsonProperty("number") String number,
    @JsonProperty("reference_title") String title,
    @JsonProperty("author_sequence") String authors,
    @JsonProperty("year") Integer year,
    @JsonProperty("categories") String category,
    @JsonProperty("keywords") List<String> keywords,
    @JsonProperty("document") List<UUID> attachmentIds,
    @JsonProperty("document_names") List<String> attachmentNames) {

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
