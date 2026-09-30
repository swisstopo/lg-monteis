package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.ExperimentWebMapper;
import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorParameterRowQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.SensorWebMapper;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every application controller mapping is covered by an explicit request rule (BR4.9, NFR1.1):
 * the mappings Spring registers must equal {@link #EXPECTED} exactly, so a new endpoint fails this
 * test until someone decides which BR4.8 rule it falls under. Each mapping is then requested with
 * every privilege level to prove the filter chain really applies that rule.
 */
@ControllerTest
class EndpointCoverageTest {

  /** The BR4.8 rule groups, with the privilege levels each lets through. */
  enum Rule {
    EDIT_EXPERIMENT(
        EnumSet.of(
            PrivilegeLevel.EXPERIMENT_PI,
            PrivilegeLevel.GLOBAL_EDITOR,
            PrivilegeLevel.MONTEIS_ADMIN)),
    CREATE_EXPERIMENT(EnumSet.of(PrivilegeLevel.MONTEIS_ADMIN)),
    MANAGE_SENSORS(EnumSet.of(PrivilegeLevel.MONTEIS_ADMIN)),
    ADMIN_WRITE(EnumSet.of(PrivilegeLevel.MONTEIS_ADMIN)),
    AUTHENTICATED(EnumSet.allOf(PrivilegeLevel.class));

    private final Set<PrivilegeLevel> allowed;

    Rule(Set<PrivilegeLevel> allowed) {
      this.allowed = allowed;
    }
  }

  private static final Map<String, Rule> EXPECTED =
      Map.ofEntries(
          Map.entry("GET /api/experiments", Rule.AUTHENTICATED),
          Map.entry("GET /api/experiments/all", Rule.AUTHENTICATED),
          Map.entry("GET /api/experiments/csv", Rule.AUTHENTICATED),
          Map.entry("GET /api/experiments/{id}", Rule.AUTHENTICATED),
          Map.entry("PUT /api/experiments/{id}", Rule.EDIT_EXPERIMENT),
          Map.entry("POST /api/experiments", Rule.CREATE_EXPERIMENT),
          Map.entry("GET /api/sensors", Rule.AUTHENTICATED),
          Map.entry("GET /api/sensors/{id}", Rule.AUTHENTICATED),
          Map.entry("GET /api/sensors/csv", Rule.AUTHENTICATED),
          Map.entry("GET /api/sensors/formulas", Rule.AUTHENTICATED),
          Map.entry("GET /api/sensors/types", Rule.AUTHENTICATED),
          Map.entry("POST /api/sensors", Rule.MANAGE_SENSORS),
          Map.entry("PUT /api/sensors/{id}", Rule.MANAGE_SENSORS),
          Map.entry("POST /api/sensors/republish-config", Rule.MANAGE_SENSORS),
          Map.entry("GET /api/measurements", Rule.AUTHENTICATED),
          Map.entry("GET /api/measurements/charts/data", Rule.AUTHENTICATED),
          Map.entry("GET /api/overview/metrics", Rule.AUTHENTICATED),
          Map.entry("GET /api/me", Rule.AUTHENTICATED));

  @Autowired private MockMvc mockMvc;
  @Autowired private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private ExperimentService experimentService;
  @MockitoBean private ExperimentWebMapper experimentWebMapper;
  @MockitoBean private ExperimentCsvExportQueryRepository experimentCsvExportQueryRepository;
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
    assertEquals(new TreeMap<>(EXPECTED).keySet(), applicationMappings().keySet());
  }

  @Test
  void should_enforce_each_mappings_rule_for_every_privilege_level() throws Exception {
    for (String mapping : applicationMappings().keySet()) {
      Rule rule = EXPECTED.get(mapping);
      assertTrue(rule != null, "no rule for " + mapping);
      for (PrivilegeLevel level : PrivilegeLevel.values()) {
        int status = perform(mapping, level).getStatus();
        String context = mapping + " as " + level;
        if (rule.allowed.contains(level)) {
          assertNotEquals(403, status, context);
          assertNotEquals(401, status, context);
        } else {
          assertEquals(403, status, context);
        }
      }
    }
  }

  @Test
  void should_reject_anonymous_callers_on_every_mapping() throws Exception {
    for (String mapping : applicationMappings().keySet()) {
      String[] parts = mapping.split(" ", 2);
      int status =
          mockMvc
              .perform(request(HttpMethod.valueOf(parts[0]), concrete(parts[1])).with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();
      assertTrue(status == 401 || status == 403, mapping + " answered " + status);
    }
  }

  private MockHttpServletResponse perform(String mapping, PrivilegeLevel level) throws Exception {
    String[] parts = mapping.split(" ", 2);
    return mockMvc
        .perform(
            request(HttpMethod.valueOf(parts[0]), concrete(parts[1]))
                .with(csrf())
                .with(authentication(level.authentication())))
        .andReturn()
        .getResponse();
  }

  // path variables: the experiment the ExperimentPI fixture may edit, any id elsewhere
  private static String concrete(String pattern) {
    String id =
        pattern.startsWith("/api/experiments")
            ? ASSIGNED_EXPERIMENT.toString()
            : UUID.randomUUID().toString();
    return pattern.replaceAll("\\{[^}]+}", id);
  }

  /** "METHOD pattern" of every mapping of a controller in this application. */
  private Map<String, HandlerMethod> applicationMappings() {
    Map<String, HandlerMethod> mappings = new TreeMap<>();
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
          mappings.put(method.name() + " " + pattern, handler);
        }
      }
    }
    return mappings;
  }
}
