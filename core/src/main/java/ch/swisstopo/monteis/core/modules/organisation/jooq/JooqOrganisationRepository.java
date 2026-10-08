package ch.swisstopo.monteis.core.modules.organisation.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.domain.OrganisationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqOrganisationRepository implements OrganisationRepository {

  private final DSLContext dsl;
  private final OrganisationJooqMapper mapper;

  public JooqOrganisationRepository(DSLContext dsl, OrganisationJooqMapper mapper) {
    this.dsl = dsl;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public Organisation create(Organisation organisation) {
    try {
      return mapper.toDomain(
          dsl.insertInto(ORGANISATIONS)
              .set(ORGANISATIONS.NAME, organisation.getName())
              .returning()
              .fetchSingle());
    } catch (DuplicateKeyException _) {
      throw new FieldBusinessValidationException(
          "name", organisation.getName(), "validation.unique", Map.of());
    }
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    int deleted = dsl.deleteFrom(ORGANISATIONS).where(ORGANISATIONS.ID.eq(id)).execute();
    if (deleted == 0) {
      throw new ObjectNotFoundException(Organisation.class);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Organisation> findAll() {
    return dsl.selectFrom(ORGANISATIONS).orderBy(ORGANISATIONS.NAME.asc()).fetch(mapper::toDomain);
  }
}
