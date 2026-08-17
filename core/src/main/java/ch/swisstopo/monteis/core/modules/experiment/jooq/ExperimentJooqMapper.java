package ch.swisstopo.monteis.core.modules.experiment.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ExperimentJooqMapper {

  // --- Parent Experiment Graph Mapping ---

  @Mapping(target = "period.start", source = "start")
  @Mapping(target = "period.end", source = "end")
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "status", ignore = true)
  Experiment toDomain(ExperimentsRecord experimentsRecord);

  @Mapping(target = "start", source = "period.start")
  @Mapping(target = "end", source = "period.end")
  ExperimentsRecord toRecord(Experiment domain);

  @Mapping(target = "start", source = "period.start")
  @Mapping(target = "end", source = "period.end")
  void updateRecordFromDomain(
      Experiment experiment, @MappingTarget ExperimentsRecord experimentsRecord);

  // --- Parent Experiment Graph Mapping ---

  @Mapping(target = "period.start", source = "start")
  @Mapping(target = "period.end", source = "end")
  ExperimentResponseDto toDto(ExperimentsRecord experimentsRecord);
}
