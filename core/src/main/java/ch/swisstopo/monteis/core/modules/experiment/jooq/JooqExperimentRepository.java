package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQuery;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentRepository implements ExperimentQuery, ExperimentRepository {

  private final DSLContext dsl;
  private final ExperimentJooqMapper mapper;

  public JooqExperimentRepository(DSLContext dsl, ExperimentJooqMapper mapper) {
    this.mapper = mapper;
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true) // required for RLS
  public ExperimentResponseDto getById(Long experimentId) {
    return null;
  }

  @Override
  @Transactional
  public Experiment create(Experiment experiment) {

    return experiment;
  }

  @Override
  @Transactional
  public Experiment update(Experiment experiment) {

    return experiment;
  }

  @Override
  @Transactional
  public Stream<Experiment> streamUnauditedExperiments() {
    return dsl.select(EXPERIMENTS.fields())
        .whereNotExists(
            dsl.selectOne()
                .from(DSL.table("jv_global_id"))
                // JaVers stores IDs as strings, so we cast it to match EXPERIMENTS.ID
                .where(DSL.field("local_id").cast(Long.class).eq(EXPERIMENTS.ID))
                // Ensure this matches your JaVers @TypeName or class name!
                .and(DSL.field("type_name").eq(Experiment.JAVERS_TYPE)))
        .fetchStream()
        .map(
            r -> {
              ExperimentsRecord experimentsRecord = r.into(EXPERIMENTS);

              return mapper.toDomain(experimentsRecord);
            });
  }
}
