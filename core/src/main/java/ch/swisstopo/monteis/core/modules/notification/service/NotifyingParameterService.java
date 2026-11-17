package ch.swisstopo.monteis.core.modules.notification.service;

import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.query.NotifyingParameterQuery;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotifyingParameterResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotifyingParameterService {

  private final ExperimentRepository experimentRepository;
  private final NotifyingParameterQuery notifyingParameterQuery;

  public NotifyingParameterService(
      ExperimentRepository experimentRepository, NotifyingParameterQuery notifyingParameterQuery) {
    this.experimentRepository = experimentRepository;
    this.notifyingParameterQuery = notifyingParameterQuery;
  }

  @Transactional(readOnly = true)
  public List<NotifyingParameterResponseDto> findNotifyingParametersOf(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    return notifyingParameterQuery.findNotifyingParametersOf(experimentId);
  }
}
