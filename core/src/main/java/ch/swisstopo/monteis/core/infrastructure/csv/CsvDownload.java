package ch.swisstopo.monteis.core.infrastructure.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public final class CsvDownload {

  public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

  private CsvDownload() {}

  public static ResponseEntity<StreamingResponseBody> attachment(String fileName, CsvRows rows) {
    return ResponseEntity.ok()
        .contentType(TEXT_CSV)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(
            outputStream -> {
              // flushed but not closed, spring flushes the stream once more after the body
              Writer writer =
                  new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
              rows.writeTo(writer);
              writer.flush();
            });
  }

  @FunctionalInterface
  public interface CsvRows {
    void writeTo(Writer writer) throws IOException;
  }
}
