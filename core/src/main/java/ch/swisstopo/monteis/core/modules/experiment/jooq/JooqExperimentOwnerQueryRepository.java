package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_OWNER;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JooqExperimentOwnerQueryRepository implements ExperimentOwnerQueryRepository {

  private final DSLContext dsl;

  public JooqExperimentOwnerQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Map<UUID, Set<UUID>> findOwnerIdsByExperiment() {
    return dsl
        .select(EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID)
        .from(EXPERIMENT_OWNER)
        .fetch()
        .stream()
        .collect(
            Collectors.groupingBy(
                r -> r.get(EXPERIMENT_OWNER.EXPERIMENT_ID),
                Collectors.mapping(r -> r.get(EXPERIMENT_OWNER.USER_ID), Collectors.toSet())));
  }
}
