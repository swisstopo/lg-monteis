package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// hand-written like FulcrumSensor, Fulcrum leaves absent values as null so every component is
// nullable
public record FulcrumLiteratureRow(
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

  // document and document_names are parallel arrays. a missing name would otherwise drop the
  // file, the id still downloads
  public Optional<FulcrumAttachmentReference> firstAttachment() {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return Optional.empty();
    }
    UUID id = attachmentIds.getFirst();
    String name =
        attachmentNames != null && !attachmentNames.isEmpty()
            ? attachmentNames.getFirst()
            : id.toString();
    return Optional.of(new FulcrumAttachmentReference(id, name));
  }

  public boolean hasSeveralAttachments() {
    return attachmentIds != null && attachmentIds.size() > 1;
  }

  public record FulcrumAttachmentReference(UUID id, String name) {}
}
