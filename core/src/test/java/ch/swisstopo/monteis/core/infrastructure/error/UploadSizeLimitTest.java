package ch.swisstopo.monteis.core.infrastructure.error;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The upload limits from application.properties on a real tomcat. mockMvc has no connection to
 * reset, so only here does it show whether the 413 reaches the client.
 */
@SpringBootTest(
    classes = UploadSizeLimitTest.UploadApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
class UploadSizeLimitTest {

  private static final String BOUNDARY = "upload-size-limit-test";

  @LocalServerPort private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void should_accept_a_file_of_exactly_the_limit() throws Exception {
    // when
    HttpResponse<String> response = upload(DataSize.ofMegabytes(50).toBytes());

    // then
    assertAll(
        () -> assertEquals(200, response.statusCode()),
        () -> assertEquals(String.valueOf(DataSize.ofMegabytes(50).toBytes()), response.body()));
  }

  @Test
  void should_answer_413_with_the_limit_for_a_file_over_it() throws Exception {
    // when
    HttpResponse<String> response = upload(DataSize.ofMegabytes(60).toBytes());

    // then
    assertAll(
        () -> assertEquals(413, response.statusCode()),
        () ->
            assertTrue(response.body().contains("\"messageKey\":\"document.validation.tooLarge\"")),
        () -> assertTrue(response.body().contains("\"max\":\"50 MB\"")));
  }

  @Test
  void should_close_the_connection_instead_of_reading_a_huge_upload() {
    assertThrows(IOException.class, () -> upload(DataSize.ofMegabytes(150).toBytes()));
  }

  // streamed with a content length, as a browser sends it, without holding the file in memory
  private HttpResponse<String> upload(long fileBytes) throws IOException, InterruptedException {
    byte[] head =
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
    long length = head.length + fileBytes + tail.length;
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/upload"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(
                HttpRequest.BodyPublishers.fromPublisher(
                    HttpRequest.BodyPublishers.ofInputStream(
                        () ->
                            new SequenceInputStream(
                                Collections.enumeration(
                                    List.of(
                                        new ByteArrayInputStream(head),
                                        new ZeroInputStream(fileBytes),
                                        new ByteArrayInputStream(tail))))),
                    length))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static final class ZeroInputStream extends InputStream {

    private long remaining;

    ZeroInputStream(long size) {
      this.remaining = size;
    }

    @Override
    public int read() {
      if (remaining <= 0) {
        return -1;
      }
      remaining--;
      return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (remaining <= 0) {
        return -1;
      }
      int count = (int) Math.min(length, remaining);
      Arrays.fill(buffer, offset, offset + count, (byte) 0);
      remaining -= count;
      return count;
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
