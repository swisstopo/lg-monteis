package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentDocumentResponseDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface ExperimentDocumentWebMapper {

  @Mapping(target = "fileName", source = "metadata.fileName")
  @Mapping(target = "contentType", source = "metadata.contentType")
  @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
  @Mapping(
      target = "viewable",
      source = "metadata.contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  ExperimentDocumentResponseDto toDto(ExperimentDocument domain);

  List<ExperimentDocumentResponseDto> toDtos(List<ExperimentDocument> domains);

  default DocumentMetadata toMetadata(MultipartFile file) {
    return DocumentMetadata.of(
        fileNameOf(file.getOriginalFilename()),
        contentTypeOf(file.getContentType()),
        file.getSize());
  }

  // some browsers send the full client path instead of the file name
  private static String fileNameOf(String originalFileName) {
    String path = originalFileName == null ? "" : originalFileName;
    return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1).strip();
  }

  private static String contentTypeOf(String contentType) {
    try {
      String normalized = MimeTypeUtils.parseMimeType(contentType).toString();
      return normalized.length() <= DocumentMetadata.MAX_LENGTH
          ? normalized
          : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    } catch (InvalidMimeTypeException _) {
      return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    }
  }
}
