package ch.swisstopo.monteis.core.modules.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.query.NotificationParameterQuery;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotificationParameterResponseDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationParameterServiceTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final NotificationParameterResponseDto TEMPERATURE =
      new NotificationParameterResponseDto(
          UUID.randomUUID(),
          "monteis-001",
          "Temperature Param",
          "SOL_EXPERTS__TEMP-1__temperature",
          "KELVIN",
          null,
          null,
          true,
          true,
          null);

  @Mock private ExperimentRepository experimentRepository;
  @Mock private NotificationParameterQuery query;

  @InjectMocks private NotificationParameterService service;

  @Test
  void should_list_the_notification_parameters_of_a_visible_experiment() {
    // given
    given(query.findNotificationParametersOf(EXPERIMENT_ID)).willReturn(List.of(TEMPERATURE));

    // when
    List<NotificationParameterResponseDto> parameters =
        service.findNotificationParametersOf(EXPERIMENT_ID);

    // then
    assertThat(parameters).containsExactly(TEMPERATURE);
  }

  @Test
  void should_fail_with_not_found_instead_of_an_empty_list_for_an_unknown_experiment() {
    // given
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireVisible(EXPERIMENT_ID);

    // when / then
    assertThatThrownBy(() -> service.findNotificationParametersOf(EXPERIMENT_ID))
        .isInstanceOf(ObjectNotFoundException.class);
    then(query).shouldHaveNoInteractions();
  }
}
