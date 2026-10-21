package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.Attachment;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Fulcrum's attachment endpoint only answers with metadata and the url of the content. the url
// client has no base url and no token, and it follows no redirects: the url comes from Fulcrum's
// answer, so only one on an attachment origin is fetched
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

  // the caller closes the stream, it holds the connection to the attachment host. a file above
  // maxSize is refused before the download when Fulcrum knows its size, and while reading otherwise
  public InputStream download(UUID attachmentId, DataSize maxSize) {
    Attachment attachment =
        attachmentOf(attachmentId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fulcrum has no attachment %s".formatted(attachmentId)));
    if (attachment.getFileSize() != null && attachment.getFileSize() > maxSize.toBytes()) {
      throw new FulcrumAttachmentTooLargeException(attachmentId, maxSize);
    }
    URI url = allowedUrlOf(attachment, attachmentId);
    return new SizeLimitedInputStream(contentAt(url, attachmentId), attachmentId, maxSize);
  }

  private Optional<Attachment> attachmentOf(UUID attachmentId) {
    try {
      return Optional.ofNullable(fulcrumApi.getSingleAttachment(attachmentId.toString(), null))
          .map(ResponseEntity::getBody);
    } catch (RestClientResponseException e) {
      throw FulcrumAuthenticationException.translateRejectedToken(e);
    }
  }

  private URI allowedUrlOf(Attachment attachment, UUID attachmentId) {
    URI url = attachment.getUrl();
    if (url == null) {
      throw new IllegalStateException(
          "Fulcrum attachment %s has no download url".formatted(attachmentId));
    }
    String origin = originOf(url);
    if (properties.attachmentOrigins().stream()
        .map(FulcrumAttachmentDownloadClient::originOf)
        .noneMatch(origin::equals)) {
      throw new IllegalStateException(
          "Fulcrum attachment %s points to %s, not to one of %s"
              .formatted(attachmentId, origin, properties.attachmentOrigins()));
    }
    return url;
  }

  // the authority keeps any user info, so user@host is refused as another origin than host
  private static String originOf(URI url) {
    return (url.getScheme() + "://" + url.getRawAuthority()).toLowerCase(Locale.ROOT);
  }

  // the url can be signed, RestClient's own messages would carry it into the log
  private InputStream contentAt(URI url, UUID attachmentId) {
    try {
      return attachmentUrlClient
          .get()
          .uri(url)
          .exchange(
              (request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                  response.close();
                  throw new IllegalStateException(
                      "Downloading Fulcrum attachment %s answered %s"
                          .formatted(attachmentId, response.getStatusCode()));
                }
                return response.getBody();
              },
              false);
    } catch (RestClientException e) {
      throw new IllegalStateException(
          "Downloading Fulcrum attachment %s failed with %s"
              .formatted(attachmentId, e.getClass().getSimpleName()));
    }
  }

  // Fulcrum's file_size can be missing or wrong, the disk is protected while copying too
  private static final class SizeLimitedInputStream extends FilterInputStream {

    private final UUID attachmentId;
    private final DataSize maxSize;
    private long read;

    private SizeLimitedInputStream(InputStream content, UUID attachmentId, DataSize maxSize) {
      super(content);
      this.attachmentId = attachmentId;
      this.maxSize = maxSize;
    }

    @Override
    public int read() throws IOException {
      int next = super.read();
      if (next != -1) {
        count(1);
      }
      return next;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int count = super.read(buffer, offset, length);
      if (count > 0) {
        count(count);
      }
      return count;
    }

    private void count(long bytes) {
      read += bytes;
      if (read > maxSize.toBytes()) {
        throw new FulcrumAttachmentTooLargeException(attachmentId, maxSize);
      }
    }
  }
}
