package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import ch.swisstopo.monteis.core.CoreApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(CoreApplication.class);

  @Test
  void sensor_write_flow_layers_should_be_respected() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain")
        .definedBy("..sensor.domain..")
        .layer("Service")
        .definedBy("..sensor.service..")
        .layer("Web")
        .definedBy("..sensor.web..")
        .layer("Persistence")
        .definedBy("..sensor.jooq..")
        .whereLayer("Web")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Service")
        .mayOnlyBeAccessedByLayers("Web")
        .whereLayer("Persistence")
        .mayNotBeAccessedByAnyLayer()
        .because(
            "the write flow (Controller -> WriteDto -> Mapper -> Domain Entity -> Service ->"
                + " Repository) requires the Domain layer to stay pure: nothing may depend on it"
                + " except Service/Web/Persistence, and the jOOQ persistence adapter is wired via"
                + " Spring, never depended on directly")
        .check(importedClasses);
  }
}
