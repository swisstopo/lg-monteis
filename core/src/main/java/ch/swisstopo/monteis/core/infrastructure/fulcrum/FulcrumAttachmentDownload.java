package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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
