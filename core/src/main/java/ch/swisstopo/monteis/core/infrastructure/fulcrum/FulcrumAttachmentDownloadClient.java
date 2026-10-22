package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.Attachment;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// url comes from Fulcrum's answer: fetched without token (must not reach content host), no
// redirects (could leave attachment origins)
@Component
public class FulcrumAttachmentDownloadClient {

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final RestClient attachmentUrlClient;

  public FulcrumAttachmentDownloadClient(
      DefaultApi fulcrumApi, FulcrumProperties properties, RestClient.Builder builder) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.attachmentUrlClient =
        builder
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        HttpClientSettings.defaults()
                            .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                            .withRedirects(HttpRedirects.DONT_FOLLOW)))
            .build();
  }

  public FulcrumAttachmentDownload download(UUID attachmentId, DataSize maxSize) {
    Attachment attachment =
        findAttachmentInFulcrum(attachmentId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fulcrum has no attachment %s".formatted(attachmentId)));
    URI url = extractValidatedUrl(attachment);
    return streamFileFrom(url, attachmentId, maxSize);
  }

  private Optional<Attachment> findAttachmentInFulcrum(UUID attachmentId) {
    try {
      return Optional.ofNullable(fulcrumApi.getSingleAttachment(attachmentId.toString(), null))
          .map(ResponseEntity::getBody);
    } catch (RestClientResponseException e) {
      throw FulcrumAuthenticationException.translateRejectedToken(e);
    }
  }

  private URI extractValidatedUrl(Attachment attachment) {
    URI url = attachment.getUrl();
    if (url == null) {
      throw new IllegalStateException(
          "Fulcrum attachment %s has no download url".formatted(attachment.getId()));
    }
    if (!properties.isAttachmentOrigin(url)) {
      throw new IllegalStateException(
          "Fulcrum attachment %s points to %s, not to one of %s"
              .formatted(
                  attachment.getId(),
                  FulcrumProperties.originOf(url),
                  properties.attachmentOrigins()));
    }
    return url;
  }

  // url may be signed, RestClient's messages would log it
  private FulcrumAttachmentDownload streamFileFrom(URI url, UUID attachmentId, DataSize maxSize) {
    try {
      return attachmentUrlClient
          .get()
          .uri(url)
          .exchange((request, response) -> openBodyOf(response, attachmentId, maxSize), false);
    } catch (RestClientException e) {
      throw new IllegalStateException(
          "Downloading Fulcrum attachment %s failed with %s"
              .formatted(attachmentId, e.getClass().getSimpleName()));
    }
  }

  // exchange keeps response open for streaming, rejected one must be closed or leaks its connection
  private static FulcrumAttachmentDownload openBodyOf(
      ClientHttpResponse response, UUID attachmentId, DataSize maxSize) throws IOException {
    try {
      rejectUnusableDownload(response, attachmentId, maxSize);
      return new FulcrumAttachmentDownload(
          response.getBody(), response.getHeaders().getContentLength());
    } catch (RuntimeException | IOException e) {
      response.close();
      throw e;
    }
  }

  // http client reads at most content length, so header caps file on disk. file_size untrusted,
  // may be missing or wrong
  private static void rejectUnusableDownload(
      ClientHttpResponse response, UUID attachmentId, DataSize maxSize) throws IOException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException(
          "Downloading Fulcrum attachment %s answered %s"
              .formatted(attachmentId, response.getStatusCode()));
    }
    long length = response.getHeaders().getContentLength();
    if (length < 0) {
      throw new IllegalStateException(
          "Downloading Fulcrum attachment %s answered without a content length"
              .formatted(attachmentId));
    }
    if (length > maxSize.toBytes()) {
      throw new FulcrumAttachmentTooLargeException(attachmentId, maxSize);
    }
  }
}
