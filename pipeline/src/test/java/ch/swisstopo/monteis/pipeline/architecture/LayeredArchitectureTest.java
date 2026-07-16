package ch.swisstopo.monteis.pipeline.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import ch.swisstopo.monteis.pipeline.PipelineApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(PipelineApplication.class);

  @Test
  void pipeline_layers_should_be_respected() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Ingress")
        .definedBy("..pipeline.ingress..")
        .layer("Transformation")
        .definedBy("..pipeline.transformation..")
        .layer("Egress")
        .definedBy("..pipeline.egress..")
        .layer("Persistence")
        .definedBy("..persistence..")
        .whereLayer("Ingress")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Transformation")
        .mayOnlyBeAccessedByLayers("Ingress")
        .whereLayer("Persistence")
        .mayOnlyBeAccessedByLayers("Transformation")
        .whereLayer("Egress")
        .mayOnlyBeAccessedByLayers("Transformation")
        .check(importedClasses);
  }
}
