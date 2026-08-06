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

  @Mapping(target = "id", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "experimentDates.experimentStart", source = "experimentStart")
  @Mapping(target = "experimentDates.experimentEnd", source = "experimentEnd")
  Experiment toDomain(ExperimentsRecord experimentsRecord);

  @Mapping(target = "description", source = "description")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "experimentStart", source = "experimentDates.experimentStart")
  @Mapping(target = "experimentEnd", source = "experimentDates.experimentEnd")
  ExperimentsRecord toRecord(Experiment domain);

  @Mapping(target = "description", source = "description")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "experimentStart", source = "experimentDates.experimentStart")
  @Mapping(target = "experimentEnd", source = "experimentDates.experimentEnd")
  void updateRecordFromDomain(
      Experiment experiment, @MappingTarget ExperimentsRecord experimentsRecord);

  // --- Parent Experiment Graph Mapping (Read Flow) ---

  @Mapping(target = "id", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "experimentDates.experimentStart", source = "experimentStart")
  @Mapping(target = "experimentDates.experimentEnd", source = "experimentEnd")
  ExperimentResponseDto toDto(ExperimentsRecord experimentsRecord);
}
