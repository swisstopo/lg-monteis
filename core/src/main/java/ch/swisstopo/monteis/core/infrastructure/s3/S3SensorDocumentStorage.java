package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class S3SensorDocumentStorage extends S3DocumentStorage implements SensorDocumentStorage {

  public S3SensorDocumentStorage(S3Client s3, DocumentStorageProperties properties) {
    super(s3, properties.bucket(), properties.sensorDocumentKeyPrefix());
  }
}
