package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.Attachment;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// the attachment endpoint only answers with metadata and a presigned S3 url, the content comes from
// that url. FulcrumConfig hands in a client without base url and token, the token must not go to S3
public class FulcrumAttachments {

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final RestClient downloadClient;

  public FulcrumAttachments(
      DefaultApi fulcrumApi, FulcrumProperties properties, RestClient downloadClient) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.downloadClient = downloadClient;
  }

  // the caller closes the stream, it holds the connection to Fulcrum's S3
  public InputStream open(UUID attachmentId) {
    Attachment attachment = findAttachment(attachmentId);
    if (attachment.getUrl() == null) {
      throw new IllegalStateException("Fulcrum attachment %s has no url".formatted(attachmentId));
    }
    return download(attachmentId, attachment.getUrl());
  }

  private Attachment findAttachment(UUID attachmentId) {
    if (properties.apiToken() == null || properties.apiToken().isBlank()) {
      throw new FulcrumAuthenticationException(
          "No Fulcrum API token configured (monteis.fulcrum.api-token)");
    }
    try {
      Attachment attachment =
          fulcrumApi.getSingleAttachment(attachmentId.toString(), null).getBody();
      if (attachment == null) {
        throw new IllegalStateException(
            "Fulcrum answered without a body for attachment " + attachmentId);
      }
      return attachment;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED
          || e.getStatusCode() == HttpStatus.FORBIDDEN) {
        throw new FulcrumAuthenticationException(
            "Fulcrum rejected the configured API token with %s".formatted(e.getStatusCode()), e);
      }
      throw e;
    }
  }

  private InputStream download(UUID attachmentId, URI url) {
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
