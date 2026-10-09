package ch.swisstopo.monteis.core.modules.organisation.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.domain.OrganisationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqOrganisationRepository implements OrganisationRepository {

  static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.of("id", ORGANISATIONS.ID, "name", ORGANISATIONS.NAME);

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
  public Organisation update(Organisation organisation) {
    try {
      return dsl.update(ORGANISATIONS)
          .set(ORGANISATIONS.NAME, organisation.getName())
          .where(ORGANISATIONS.ID.eq(organisation.getId()))
          .returning()
          .fetchOptional()
          .map(mapper::toDomain)
          .orElseThrow(() -> new ObjectNotFoundException(Organisation.class));
    } catch (DuplicateKeyException _) {
      throw new FieldBusinessValidationException(
          "name", organisation.getName(), "validation.unique", Map.of());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Organisation> findById(UUID id) {
    return dsl.selectFrom(ORGANISATIONS)
        .where(ORGANISATIONS.ID.eq(id))
        .fetchOptional(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Organisation> findAll() {
    return dsl.selectFrom(ORGANISATIONS).orderBy(ORGANISATIONS.NAME.asc()).fetch(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Organisation> getOrganisations(PagedRequest request) {
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(request, COLUMNS_BY_COL_ID, ORGANISATIONS.ID.asc());

    List<Organisation> data =
        dsl.selectFrom(ORGANISATIONS)
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(mapper::toDomain);

    int totalCount = dsl.fetchCount(ORGANISATIONS, criteria.condition());

    return new PagedResult<>(data, totalCount);
  }
}
