package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.Attachment;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Fulcrum's attachment endpoint only answers with metadata and a presigned S3 url, the content
// comes from that url. the download client has no base url and no token, the token must not go to
// S3
@Component
public class FulcrumAttachmentDownloadClient {

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final RestClient downloadClient;

  public FulcrumAttachmentDownloadClient(
      DefaultApi fulcrumApi, FulcrumProperties properties, RestClient.Builder builder) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.downloadClient =
        builder.requestFactory(FulcrumConfig.requestFactoryOf(properties)).build();
  }

  // the caller closes the stream, it holds the connection to Fulcrum's S3
  public InputStream download(UUID attachmentId) {
    properties.requireEnabled();
    URI url =
        downloadUrlOf(attachmentId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fulcrum attachment %s has no download url".formatted(attachmentId)));
    return contentAt(url, attachmentId);
  }

  private Optional<URI> downloadUrlOf(UUID attachmentId) {
    try {
      return Optional.ofNullable(fulcrumApi.getSingleAttachment(attachmentId.toString(), null))
          .map(ResponseEntity::getBody)
          .map(Attachment::getUrl);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED
          || e.getStatusCode() == HttpStatus.FORBIDDEN) {
        throw new FulcrumAuthenticationException(
            "Fulcrum rejected the configured API token with %s".formatted(e.getStatusCode()), e);
      }
      throw e;
    }
  }

  private InputStream contentAt(URI url, UUID attachmentId) {
    return downloadClient
        .get()
        .uri(url)
        .exchange(
            (request, response) -> {
              if (response.getStatusCode().isError()) {
                response.close();
                throw new IllegalStateException(
                    "Downloading Fulcrum attachment %s answered %s"
                        .formatted(attachmentId, response.getStatusCode()));
              }
              return response.getBody();
            },
            false);
  }
}
