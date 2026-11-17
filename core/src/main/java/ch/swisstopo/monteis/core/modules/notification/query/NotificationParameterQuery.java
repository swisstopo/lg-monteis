package ch.swisstopo.monteis.core.modules.notification.query;

import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotificationParameterResponseDto;
import java.util.List;
import java.util.UUID;

public interface NotificationParameterQuery {

  List<NotificationParameterResponseDto> findNotificationParametersOf(UUID experimentId);
}
