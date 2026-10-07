package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentDocumentService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.ExperimentDocumentWebMapper;
import ch.swisstopo.monteis.core.modules.experiment.web.ExperimentWebMapper;
import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorParameterRowQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.SensorWebMapper;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every application controller mapping is covered by an explicit request rule (BR4.9, NFR1.1):
 * the mappings Spring registers must equal {@link #EXPECTED} exactly, so a new endpoint fails this
 * test until someone decides which {@link SecurityConfig} rule it falls under and adds it here.
 * Whether the rules themselves hold for each privilege level is {@code
 * SecurityConfigAuthorizationTest}'s job.
 */
@ControllerTest
class EndpointCoverageTest {

  private static final Set<String> EXPECTED =
      Set.of(
          "GET /api/experiments",
          "GET /api/experiments/all",
          "GET /api/experiments/csv",
          "GET /api/experiments/{id}",
          "PUT /api/experiments/{id}",
          "GET /api/experiments/owners",
          "GET /api/experiments/{id}/owner-candidates",
          "PUT /api/experiments/{id}/owners",
          "POST /api/experiments",
          "GET /api/experiments/{id}/documents",
          "POST /api/experiments/{id}/documents",
          "GET /api/experiments/{id}/documents/{documentId}/content",
          "GET /api/sensors",
          "GET /api/sensors/{id}",
          "GET /api/sensors/csv",
          "GET /api/sensors/formulas",
          "GET /api/sensors/types",
          "POST /api/sensors",
          "PUT /api/sensors/{id}",
          "POST /api/sensors/republish-config",
          "GET /api/measurements",
          "GET /api/measurements/charts/data",
          "GET /api/overview/metrics",
          "GET /api/me");

  @Autowired private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private ExperimentService experimentService;
  @MockitoBean private ExperimentOwnerService experimentOwnerService;
  @MockitoBean private ExperimentWebMapper experimentWebMapper;
  @MockitoBean private ExperimentCsvExportQueryRepository experimentCsvExportQueryRepository;
  @MockitoBean private ExperimentDocumentService experimentDocumentService;
  @MockitoBean private ExperimentDocumentWebMapper experimentDocumentWebMapper;
  @MockitoBean private SensorService sensorService;
  @MockitoBean private SensorWebMapper sensorWebMapper;
  @MockitoBean private SensorCsvExportQueryRepository sensorCsvExportQueryRepository;
  @MockitoBean private SensorParameterRowQueryRepository sensorParameterRowQueryRepository;
  @MockitoBean private MeasurementService measurementService;
  @MockitoBean private OverviewService overviewService;
  @MockitoBean private PagedRequestParser pagedRequestParser;
  @MockitoBean private Clock clock;

  @Test
  void should_have_an_explicit_rule_for_every_application_mapping() {
    assertEquals(new TreeSet<>(EXPECTED), applicationMappings());
  }

  /** "METHOD pattern" of every mapping of a controller in this application. */
  private Set<String> applicationMappings() {
    Set<String> mappings = new TreeSet<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().getPackageName().startsWith("ch.swisstopo.monteis")) {
        continue; // framework mappings such as /error
      }
      Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
      assertTrue(
          !methods.isEmpty(),
          handler + " accepts every HTTP method; declare the method so a rule can apply");
      for (String pattern : entry.getKey().getPatternValues()) {
        for (RequestMethod method : methods) {
          mappings.add(method.name() + " " + pattern);
        }
      }
    }
    return mappings;
  }
}
