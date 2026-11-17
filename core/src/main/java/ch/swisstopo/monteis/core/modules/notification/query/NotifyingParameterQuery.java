package ch.swisstopo.monteis.core.modules.notification.query;

import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotifyingParameterResponseDto;
import java.util.List;
import java.util.UUID;

public interface NotifyingParameterQuery {

  List<NotifyingParameterResponseDto> findNotifyingParametersOf(UUID experimentId);
}
