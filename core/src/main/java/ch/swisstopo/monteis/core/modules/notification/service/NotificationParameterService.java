package ch.swisstopo.monteis.core.modules.notification.service;

import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.query.NotificationParameterQuery;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotificationParameterResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationParameterService {

  private final ExperimentRepository experimentRepository;
  private final NotificationParameterQuery notificationParameterQuery;

  public NotificationParameterService(
      ExperimentRepository experimentRepository,
      NotificationParameterQuery notificationParameterQuery) {
    this.experimentRepository = experimentRepository;
    this.notificationParameterQuery = notificationParameterQuery;
  }

  @Transactional(readOnly = true)
  public List<NotificationParameterResponseDto> findNotificationParametersOf(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    return notificationParameterQuery.findNotificationParametersOf(experimentId);
  }
}
