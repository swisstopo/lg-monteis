package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentDocumentResponseDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExperimentDocumentWebMapper {
  ExperimentDocumentResponseDto toDto(ExperimentDocument domain);

  List<ExperimentDocumentResponseDto> toDtos(List<ExperimentDocument> domains);
}
