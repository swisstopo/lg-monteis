package ch.swisstopo.monteis.core.infrastructure.document;

import java.io.InputStream;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DocumentUploads {

  private static final Logger log = LoggerFactory.getLogger(DocumentUploads.class);

  private DocumentUploads() {}

  // no transaction around both: it would block a db connection for as long as S3 takes. S3 first,
  // so a failure in between leaves an object nobody sees instead of a document without content
  public static <T> T storeThenRecord(
      DocumentStorage storage,
      DocumentKey key,
      DocumentMetadata metadata,
      InputStream content,
      Supplier<T> record) {
    storage.store(key, metadata, content);
    try {
      return record.get();
    } catch (RuntimeException e) {
      deleteStored(storage, key, e);
      throw e;
    }
  }

  private static void deleteStored(
      DocumentStorage storage, DocumentKey key, RuntimeException cause) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      cause.addSuppressed(e);
      log.warn(
          "could not delete the orphaned document {} of {}", key.documentId(), key.parentId(), e);
    }
  }
}
