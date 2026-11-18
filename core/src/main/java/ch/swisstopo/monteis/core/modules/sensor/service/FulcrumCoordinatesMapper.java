package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import java.util.Optional;

final class FulcrumCoordinatesMapper {

  private FulcrumCoordinatesMapper() {}

  static Optional<Coordinates> coordinatesOf(FulcrumSensor fulcrumSensor) {
    if (!fulcrumSensor.missingCoordinateAxes().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Coordinates(
            fulcrumSensor.xPointWithOffset(),
            fulcrumSensor.yPointWithOffset(),
            fulcrumSensor.zPointWithOffset()));
  }
}
