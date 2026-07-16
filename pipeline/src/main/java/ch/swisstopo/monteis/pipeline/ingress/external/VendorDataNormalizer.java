package ch.swisstopo.monteis.pipeline.ingress.external;

import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.List;
import org.springframework.messaging.Message;

/**
 * Standard contract for translating raw vendor sensor payloads into
 * standardized Spring Messages routed by their target Kafka device key.
 *
 * @param <T> The vendor-specific raw payload type.
 */
public interface VendorDataNormalizer<T> {

  List<Message<NormalizedSensorData>> normalizeToMessages(T rawVendorPayload);
}
