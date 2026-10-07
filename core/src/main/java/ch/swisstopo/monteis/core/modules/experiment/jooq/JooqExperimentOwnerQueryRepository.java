package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_OWNER;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnership;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
  public List<ExperimentOwnership> findOwnerships() {
    Map<UUID, List<UUID>> ownerIdsByExperiment =
        dsl.select(EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID)
            .from(EXPERIMENT_OWNER)
            .fetchGroups(EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID);
    return ownerIdsByExperiment.entrySet().stream()
        .map(entry -> new ExperimentOwnership(entry.getKey(), Set.copyOf(entry.getValue())))
        .toList();
  }
}
