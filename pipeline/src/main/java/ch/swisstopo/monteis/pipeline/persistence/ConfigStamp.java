package ch.swisstopo.monteis.pipeline.persistence;

import java.util.UUID;

public record ConfigStamp(String dasKey, UUID sensorParameterId, short version) {}
