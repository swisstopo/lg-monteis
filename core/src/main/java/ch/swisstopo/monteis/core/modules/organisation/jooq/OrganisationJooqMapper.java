package ch.swisstopo.monteis.core.modules.organisation.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.OrganisationsRecord;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganisationJooqMapper {

  Organisation toDomain(OrganisationsRecord organisationsRecord);
}
