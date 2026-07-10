// package ch.swisstopo.monteis.core.modules.demo.service;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.junit.jupiter.api.Assertions.assertThrows;
//
// import ch.swisstopo.monteis.core.IT;
// import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
// import org.javers.core.Javers;
// import org.javers.core.metamodel.object.SnapshotType;
// import org.javers.repository.jql.QueryBuilder;
// import org.jooq.exception.DataChangedException;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.transaction.annotation.Transactional;
//
// @IT
// @Transactional
// class DemoServiceIT {
//
//  @Autowired private DemoService demoService;
//
//  @Autowired private Javers javers;
//
//  @Test
//  void should_prevent_audit_log_on_optimistic_locking_failure() {
//    // given: an existing sensor in the database
//    var initialDto = new WriteSensorDto(null, "sensor-lock-test", 100.0, 0.0, null, "x * 2",
// null);
//    var savedSensor = demoService.saveOrUpdateSensor(initialDto);
//
//    // and a concurrent modification that bumps the version
//    var concurrentUpdate =
//        new WriteSensorDto(
//            savedSensor.id(),
//            "sensor-lock-test",
//            150.0,
//            0.0,
//            savedSensor.version(),
//            "x * 2",
//            savedSensor.formulaVersion());
//    demoService.saveOrUpdateSensor(concurrentUpdate);
//
//    // when: a save is attempted to use stale version data
//    var staleUpdate =
//        new WriteSensorDto(
//            savedSensor.id(),
//            "sensor-lock-test",
//            50.0,
//            0.0,
//            savedSensor.version(),
//            "x * 2",
//            savedSensor.formulaVersion());
//
//    // then: the system throws an optimistic locking exception
//    assertThrows(
//        DataChangedException.class,
//        () -> {
//          demoService.saveOrUpdateSensor(staleUpdate);
//        });
//
//    // and the failed update is strictly excluded from the JaVers audit log
//    var snapshots =
//        javers.findSnapshots(
//            QueryBuilder.byInstanceId(savedSensor.id(), WriteSensorDto.class).build());
//    assertThat(snapshots).hasSize(2); // Only the initial insert and the concurrent update exist
//  }
//
//  @Test
//  void should_audit_successful_insert_and_update() {
//    // given: a payload for a brand-new sensor
//    var initialDto = new WriteSensorDto(null, "sensor-happy-path", 100.0, 0.0, null, "x * 2",
// null);
//
//    // when: the sensor is inserted
//    var savedSensor = demoService.saveOrUpdateSensor(initialDto);
//
//    // then: the version is initialized and the insert is audited
//    var snapshotsAfterInsert =
//        javers.findSnapshots(
//            QueryBuilder.byInstanceId(savedSensor.id(), WriteSensorDto.class).build());
//    assertThat(snapshotsAfterInsert).hasSize(1);
//
//    // and the INITIAL snapshot contains the exact expected values for all fields
//    var insertSnapshot = snapshotsAfterInsert.getFirst();
//    assertThat(insertSnapshot.getType()).isEqualTo(SnapshotType.INITIAL);
//    assertThat(insertSnapshot.getPropertyValue("code")).isEqualTo("sensor-happy-path");
//    assertThat(insertSnapshot.getPropertyValue("upperBound")).isEqualTo(100.0);
//    assertThat(insertSnapshot.getPropertyValue("lowerBound")).isEqualTo(0.0);
//    assertThat(insertSnapshot.getPropertyValue("version")).isEqualTo(1);
//    assertThat(insertSnapshot.getPropertyValue("expression")).isEqualTo("x * 2");
//    assertThat(insertSnapshot.getPropertyValue("formulaVersion")).isEqualTo(1);
//
//    // when: the sensor is subsequently updated with completely new data
//    var updateDto =
//        new WriteSensorDto(
//            savedSensor.id(),
//            "sensor-happy-path-updated",
//            200.0,
//            -10.0,
//            savedSensor.version(),
//            "x * 3",
//            savedSensor.formulaVersion());
//    demoService.saveOrUpdateSensor(updateDto);
//
//    // then: the update is appended to the audit log
//    var snapshotsAfterUpdate =
//        javers.findSnapshots(
//            QueryBuilder.byInstanceId(savedSensor.id(), WriteSensorDto.class).build());
//    assertThat(snapshotsAfterUpdate).hasSize(2);
//
//    // and the newest snapshot (index 0) contains the updated values
//    var latestSnapshot = snapshotsAfterUpdate.getFirst();
//    assertThat(latestSnapshot.getType()).isEqualTo(SnapshotType.UPDATE);
//    assertThat(latestSnapshot.getPropertyValue("code")).isEqualTo("sensor-happy-path-updated");
//    assertThat(latestSnapshot.getPropertyValue("upperBound")).isEqualTo(200.0);
//    assertThat(latestSnapshot.getPropertyValue("lowerBound")).isEqualTo(-10.0);
//    assertThat(latestSnapshot.getPropertyValue("version")).isEqualTo(2);
//    assertThat(latestSnapshot.getPropertyValue("expression")).isEqualTo("x * 3");
//    assertThat(latestSnapshot.getPropertyValue("formulaVersion")).isEqualTo(2);
//
//    // and the older snapshot (index 1) retains its historical values
//    var historicalSnapshot = snapshotsAfterUpdate.get(1);
//    assertThat(historicalSnapshot.getType()).isEqualTo(SnapshotType.INITIAL);
//    assertThat(historicalSnapshot.getPropertyValue("code")).isEqualTo("sensor-happy-path");
//    assertThat(historicalSnapshot.getPropertyValue("upperBound")).isEqualTo(100.0);
//  }
//
//  @Test
//  void should_ignore_ghost_updates() {
//    // given: a successfully saved sensor
//    var initialDto = new WriteSensorDto(null, "sensor-ghost-test", 100.0, 0.0, null, "x * 2",
// null);
//    var savedSensor = demoService.saveOrUpdateSensor(initialDto);
//
//    // when: a save request is made using the exact same data
//    var ghostUpdateDto =
//        new WriteSensorDto(
//            savedSensor.id(),
//            "sensor-ghost-test",
//            100.0,
//            0.0,
//            savedSensor.version(),
//            "x * 2",
//            savedSensor.formulaVersion());
//    var resultSensor = demoService.saveOrUpdateSensor(ghostUpdateDto);
//
//    // then: the database version remains untouched
//    assertThat(resultSensor.version()).isEqualTo(1);
//    assertThat(resultSensor.formulaVersion()).isEqualTo(1);
//
//    // and the audit log creates no useless snapshot entries
//    var snapshots =
//        javers.findSnapshots(
//            QueryBuilder.byInstanceId(savedSensor.id(), WriteSensorDto.class).build());
//    assertThat(snapshots).hasSize(1);
//  }
//
//  @Test
//  void should_throw_exception_when_updating_with_null_version() {
//    // given: an existing sensor in the database
//    var initialDto = new WriteSensorDto(null, "sensor-001", 100.0, 0.0, null, "x * 2", null);
//    var savedSensor = demoService.saveOrUpdateSensor(initialDto);
//
//    // when: an update is attempted but the client omits the version data (passes nulls)
//    var updateWithNullVersion =
//        new WriteSensorDto(savedSensor.id(), "sensor-001-updated", 200.0, 0.0, null, "x * 3",
// null);
//
//    // then: jOOQ's native optimistic locking treats 'null' as a mismatch and throws an exception
//    assertThrows(
//        DataChangedException.class,
//        () -> {
//          demoService.saveOrUpdateSensor(updateWithNullVersion);
//        });
//
//    // and the failed invalid update is strictly excluded from the JaVers audit log
//    var snapshots =
//        javers.findSnapshots(
//            QueryBuilder.byInstanceId(savedSensor.id(), WriteSensorDto.class).build());
//    assertThat(snapshots).hasSize(1); // Only the initial insert remains
//  }
// }
