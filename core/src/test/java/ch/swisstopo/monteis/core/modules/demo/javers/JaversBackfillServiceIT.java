package ch.swisstopo.monteis.core.modules.demo.javers;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Formulas.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;
import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.IT;
import ch.swisstopo.monteis.core.infrastructure.javers.JaversBackfillService;
import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
import org.javers.core.Javers;
import org.javers.core.metamodel.object.SnapshotType;
import org.javers.repository.jql.QueryBuilder;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IT
class JaversBackfillServiceIT {

  @Autowired private JaversBackfillService javersBackfillService;

  @Autowired private DSLContext dsl;

  @Autowired private Javers javers;

  @Autowired private TransactionTemplate transactionTemplate;

  @Test
  void should_backfill_sensors_missing_from_javers() {
    // given: a sensor inserted directly into the DB (simulating a Flyway seed script).
    var sensorId =
        dsl.insertInto(SENSORS)
            .set(SENSORS.CODE, "flyway-seeded-sensor")
            .set(SENSORS.UPPER_BOUND, 100.0)
            .set(SENSORS.LOWER_BOUND, 0.0)
            .returning(SENSORS.ID)
            .fetchOne()
            .getId();

    dsl.insertInto(FORMULAS)
        .set(FORMULAS.SENSOR_ID, sensorId)
        .set(FORMULAS.EXPRESSION, "x * 10")
        .execute();

    // verify: JaVers genuinely has no idea this sensor exists yet
    // We use transactionTemplate to safely close the DB connection after querying JaVers
    var snapshotsBefore =
        transactionTemplate.execute(
            status ->
                javers.findSnapshots(
                    QueryBuilder.byInstanceId(sensorId, WriteSensorDto.class).build()));
    assertThat(snapshotsBefore).isEmpty();

    // when: the application startup backfill service executes
    javersBackfillService.backfillMissingSnapshots();

    // then: JaVers should now have an INITIAL snapshot for this seeded sensor
    var snapshotsAfter =
        transactionTemplate.execute(
            status ->
                javers.findSnapshots(
                    QueryBuilder.byInstanceId(sensorId, WriteSensorDto.class).build()));
    assertThat(snapshotsAfter).hasSize(1);

    // and the snapshot correctly attributes the commit to the seeder with the right data
    var snapshot = snapshotsAfter.getFirst();
    assertThat(snapshot.getType()).isEqualTo(SnapshotType.INITIAL);
    assertThat(snapshot.getCommitMetadata().getAuthor()).isEqualTo("SYSTEM_SEEDER");
    assertThat(snapshot.getPropertyValue("code")).isEqualTo("flyway-seeded-sensor");
    assertThat(snapshot.getPropertyValue("expression")).isEqualTo("x * 10");
  }
}
