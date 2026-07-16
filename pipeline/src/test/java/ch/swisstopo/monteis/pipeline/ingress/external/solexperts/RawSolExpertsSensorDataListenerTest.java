package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.pipeline.ingress.external.VendorDataNormalizer;
import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

@ExtendWith(MockitoExtension.class)
class RawSolExpertsSensorDataListenerTest {

  @Mock private VendorDataNormalizer<RawSolExpertsSensorData> normalizer;

  @InjectMocks private RawSolExpertsSensorDataListener listener;

  @Test
  void should_delegate_payload_to_normalizer_and_return_outbound_messages() {
    // given
    RawSolExpertsSensorData payload =
        new RawSolExpertsSensorData("deviceA", "2026-06-23T09:00:00Z", Collections.emptyMap());

    @SuppressWarnings("unchecked")
    List<Message<NormalizedSensorData>> expectedMessages =
        List.of(org.mockito.Mockito.mock(Message.class));

    given(normalizer.normalizeToMessages(payload)).willReturn(expectedMessages);

    // when
    List<Message<NormalizedSensorData>> actualMessages = listener.processRawMessage(payload);

    // then
    then(normalizer).should().normalizeToMessages(payload);
    assertThat(actualMessages).isSameAs(expectedMessages);
  }
}
