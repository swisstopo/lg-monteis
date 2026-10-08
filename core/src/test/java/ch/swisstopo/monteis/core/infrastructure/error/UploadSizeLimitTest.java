package ch.swisstopo.monteis.core.infrastructure.error;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The upload limits from application.properties on a real tomcat. mockMvc has no connection to
 * close, so only here does it show what a client gets back.
 */
@SpringBootTest(
    classes = UploadSizeLimitTest.UploadApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
class UploadSizeLimitTest {

  // the webapp refuses a too large file before uploading it, with this copy of the limit
  private static final Path WEBAPP_LIMIT =
      Path.of("../webapp/src/app/features/experiment/experiment-documents/max-document-size.ts");

  @Value("${spring.servlet.multipart.max-file-size}")
  private DataSize maxFileSize;

  @Value("${spring.servlet.multipart.max-request-size}")
  private DataSize maxRequestSize;

  @LocalServerPort private int port;
  @TempDir private Path tempDir;

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void should_accept_a_file_of_exactly_the_limit() throws Exception {
    // when
    HttpResponse<String> response = upload(maxFileSize);

    // then
    assertAll(
        () -> assertEquals(200, response.statusCode()),
        () -> assertEquals(maxFileSize.toBytes(), Long.parseLong(response.body())));
  }

  // within the request limit tomcat reads the whole request before refusing the file, so the 413
  // reaches the client
  @Test
  void should_answer_413_with_the_limit_for_a_file_just_over_it() throws Exception {
    // when
    HttpResponse<String> response = upload(DataSize.ofBytes(maxFileSize.toBytes() + 1));

    // then
    assertAll(
        () -> assertEquals(413, response.statusCode()),
        () ->
            assertEquals(
                "document.validation.tooLarge", JsonPath.read(response.body(), "$.messageKey")),
        () ->
            assertEquals(
                maxFileSize.toMegabytes() + " MB", JsonPath.read(response.body(), "$.params.max")));
  }

  // tomcat refuses it by its content length and reads only 2 MB of the rest before closing the
  // connection, so no client can make it read a huge body
  @Test
  void should_close_the_connection_for_a_request_over_the_request_limit() {
    DataSize overRequestLimit = DataSize.ofBytes(maxRequestSize.toBytes() + 1);

    assertThrows(IOException.class, () -> upload(overRequestLimit));
  }

  @Test
  void should_be_the_limit_the_webapp_checks_before_uploading() throws IOException {
    // when
    Matcher webappLimit =
        Pattern.compile("MAX_DOCUMENT_SIZE_MB = ([0-9]+);").matcher(Files.readString(WEBAPP_LIMIT));

    // then
    assertTrue(webappLimit.find(), "no MAX_DOCUMENT_SIZE_MB in " + WEBAPP_LIMIT);
    assertEquals(maxFileSize.toMegabytes(), Long.parseLong(webappLimit.group(1)));
  }

  private HttpResponse<String> upload(DataSize fileSize) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/upload"))
            .header(HttpHeaders.CONTENT_TYPE, MultipartFileBody.CONTENT_TYPE)
            .POST(MultipartFileBody.of(fileOf(fileSize)))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  // sparse, so even the largest file costs neither memory nor disk
  private Path fileOf(DataSize size) throws IOException {
    Path file = tempDir.resolve(size.toBytes() + ".bin");
    try (RandomAccessFile zeros = new RandomAccessFile(file.toFile(), "rw")) {
      zeros.setLength(size.toBytes());
    }
    return file;
  }

  /**
   * A multipart body with one file part. it has a content length, as a browser's has, tomcat
   * refuses a request over the request limit by it before reading any of it.
   */
  private static final class MultipartFileBody {

    private static final String BOUNDARY = "upload-size-limit-test";
    static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    private static final String PART_HEAD =
        """
        --%s
        Content-Disposition: form-data; name="file"; filename="upload.bin"
        Content-Type: application/octet-stream

        """
            .formatted(BOUNDARY);

    private static final String BODY_END =
        """

        --%s--
        """
            .formatted(BOUNDARY);

    static BodyPublisher of(Path file) throws IOException {
      return BodyPublishers.concat(
          withCrlf(PART_HEAD), BodyPublishers.ofFile(file), withCrlf(BODY_END));
    }

    // multipart wants CRLF line breaks, a text block has LF ones
    private static BodyPublisher withCrlf(String text) {
      return BodyPublishers.ofString(text.replace("\n", "\r\n"));
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    MultipartAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class,
    JacksonAutoConfiguration.class
  })
  @Import({UploadController.class, GlobalErrorControllerAdvice.class})
  static class UploadApp {}

  @RestController
  static class UploadController {

    @PostMapping("/upload")
    long upload(@RequestPart("file") MultipartFile file) {
      return file.getSize();
    }
  }
}
