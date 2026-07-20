package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensorService {
  private final SensorRepository repository;
  private final Javers javers;

  public SensorService(SensorRepository repository, Javers javers) {
    this.repository = repository;
    this.javers = javers;
  }

  @AuditChanges
  public Sensor createSensor(Sensor sensor) {
    return repository.create(sensor);
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    return repository.update(sensor);
  }

  @Profile("!openapi")
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void backfillMissingSnapshots() {
    // No HTTP request/JWT exists at startup, so this explicitly opts into elevated DB
    // access rather than relying on the fail-closed default for an unbound SecurityContext.
    SystemSecurityContext.runAsSystem(
        () -> {
          try (Stream<Sensor> unauditedSensorsStream = repository.streamUnauditedSensors()) {
            unauditedSensorsStream.forEach(sensor -> javers.commit("SYSTEM_SEEDER", sensor));
          }
        });
  }
}
