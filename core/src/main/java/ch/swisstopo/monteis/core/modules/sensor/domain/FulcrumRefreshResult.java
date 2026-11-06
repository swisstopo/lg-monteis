package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.List;

public record FulcrumRefreshResult(int checked, int updated, List<SkippedSensor> skipped) {}
