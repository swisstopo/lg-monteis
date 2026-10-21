package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

// the content holds the connection to the attachment host until it is closed. sizeBytes is the
// content length the host answered with, the content is never longer
public record FulcrumAttachmentDownload(InputStream content, long sizeBytes)
    implements AutoCloseable {

  @Override
  public void close() {
    try {
      content.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
