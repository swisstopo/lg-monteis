package ch.swisstopo.monteis.core.modules.demo.web;

import ch.swisstopo.monteis.core.modules.demo.service.DemoService;
import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @GetMapping("/metrics")
  public List<ReadSimpleMetric> getMetrics(@RequestParam(defaultValue = "5") int limit) {

    return demoService.fetchRecentMetrics(limit);
  }
}
