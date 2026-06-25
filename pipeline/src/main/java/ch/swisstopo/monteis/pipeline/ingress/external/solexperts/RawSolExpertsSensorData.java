package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import java.util.Map;

public record RawSolExpertsSensorData(String deviceName, String ts, Map<String, Double> values) {}
