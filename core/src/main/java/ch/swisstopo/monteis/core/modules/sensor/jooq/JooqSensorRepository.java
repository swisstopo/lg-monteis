package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FORMULAS;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqSensorRepository implements SensorRepository {
  private final DSLContext dsl;
  private final SensorJooqMapper mapper;

  public JooqSensorRepository(DSLContext dsl, SensorJooqMapper mapper) {
    this.dsl = dsl;
    this.mapper = mapper;
  }

  @Override
  @Transactional // Ensures the formula find/create + sensor insert happen in a single transaction
  public Sensor save(Sensor sensor) {
    Formula inboundFormula = sensor.getFormula();
    FormulasRecord formulaRecord = null;

    // 1. Look up by ID (Primary key is guaranteed unique, so fetchOne() is safe here)
    if (inboundFormula.getId() != null) {
      formulaRecord =
          dsl.selectFrom(FORMULAS).where(FORMULAS.ID.eq(inboundFormula.getId())).fetchOne();
    }

    // 2. Look up by Expression string
    if (formulaRecord == null) {
      formulaRecord =
          dsl.selectFrom(FORMULAS)
              .where(FORMULAS.EXPRESSION.eq(inboundFormula.getExpression()))
              .fetchAny(); // FIX: Safely handles duplicate "x" rows in the DB
    }

    // 3. Insert if completely missing
    if (formulaRecord == null) {
      formulaRecord = dsl.newRecord(FORMULAS);
      formulaRecord.setExpression(inboundFormula.getExpression());
      formulaRecord.insert();
    }

    // Bind and store the sensor
    sensor.setFormula(mapper.toDomain(formulaRecord));

    SensorsRecord sensorRecord = mapper.toRecord(sensor);
    sensorRecord.attach(dsl.configuration());
    sensorRecord.store();

    return mapper.toDomain(sensorRecord, formulaRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Formula> findAllFormulas() {
    return dsl.selectFrom(FORMULAS)
        .orderBy(FORMULAS.EXPRESSION.asc()) // Clean alphabetical sorting for the UI
        .fetch(mapper::toDomain); // Reuses the inner formula mapper method
  }
}
