package ch.swisstopo.monteis.core.modules.experiment.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ExperimentJooqMapper {

  // --- Parent Experiment Graph Mapping ---

  // MapStruct automatically maps id, name, owner, description, and status
  // because the field names match exactly between source and target.
  @Mapping(target = "experimentStart", source = "startDate")
  @Mapping(target = "experimentEnd", source = "endDate")
  Experiment toDomain(ExperimentsRecord experimentsRecord);

  @Mapping(target = "startDate", source = "experimentStart")
  @Mapping(target = "endDate", source = "experimentEnd")
  ExperimentsRecord toRecord(Experiment domain);

  @Mapping(target = "startDate", source = "experimentStart")
  @Mapping(target = "endDate", source = "experimentEnd")
  void updateRecordFromDomain(
      Experiment experiment, @MappingTarget ExperimentsRecord experimentsRecord);

  // --- Parent Experiment Graph Mapping (Read Flow) ---
  // Projects the joined jOOQ records straight into the response DTO, bypassing the Domain
  // entirely - the read flow never needs an Experiment instance.
  @Mapping(target = "experimentStart", source = "startDate")
  @Mapping(target = "experimentEnd", source = "endDate")
  ExperimentResponseDto toDto(ExperimentsRecord experimentsRecord);

  default LocalDate map(OffsetDateTime value) {
    if (value == null) {
      return null;
    }
    return value.toLocalDate();
  }

  default OffsetDateTime map(LocalDate value) {
    if (value == null) {
      return null;
    }
    // Defaults to start of the day at UTC. Change ZoneOffset if needed.
    return value.atStartOfDay().atOffset(ZoneOffset.UTC);
  }
}
