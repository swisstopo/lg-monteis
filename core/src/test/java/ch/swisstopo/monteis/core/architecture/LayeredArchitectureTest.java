package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import ch.swisstopo.monteis.core.CoreApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

  // Every feature module lives directly under this package, e.g. "..modules.sensor.domain..".
  // Layers below are defined by package pattern (not by an enumerated module list), so this
  // rule automatically covers any module added in the future.
  private static final DescribedPredicate<JavaClass> DTO_PACKAGE =
      resideInAPackage("..core.modules..web.dto..");
  private static final DescribedPredicate<JavaClass> WRITE_DTO_PACKAGE =
      resideInAPackage("..core.modules..web.dto.inbound..");

  // Controllers, MapStruct mappers, bean-validation classes and WriteDtos: everything under a
  // module's "web" package except the ReadDtos, which get their own layer below.
  private static final DescribedPredicate<JavaClass> WEB_ENTRYPOINT =
      resideInAPackage("..core.modules..web..")
          .and(not(DTO_PACKAGE))
          .or(WRITE_DTO_PACKAGE)
          .as("controllers, mappers, validators and write DTOs of any core module");

  // DTOs returned straight from a Query/Persistence method, bypassing the Domain entirely, e.g.
  // FormulaResponseDto, ReadExperimentDetailsDto, ReadSimpleMetricDto.
  private static final DescribedPredicate<JavaClass> READ_DTO =
      DTO_PACKAGE.and(not(WRITE_DTO_PACKAGE)).as("read DTOs of any core module");

  @Test
  void write_flow_layers_should_be_respected_by_every_module() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain")
        .definedBy("..core.modules..domain..")
        .layer("Service")
        .definedBy("..core.modules..service..")
        .layer("Query")
        .definedBy("..core.modules..query..")
        .layer("Web")
        .definedBy(WEB_ENTRYPOINT)
        .layer("ReadDto")
        .definedBy(READ_DTO)
        .layer("Persistence")
        .definedBy("..core.modules..jooq..")
        .whereLayer("Web")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Service")
        .mayOnlyBeAccessedByLayers("Web")
        .whereLayer("Persistence")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("ReadDto")
        .mayOnlyBeAccessedByLayers("Web", "Query", "Service", "Persistence")
        .because(
            "every module follows the CQRS-lite convention from arc42 ch. 8: the write flow"
                + " (Controller -> WriteDto -> Mapper -> Domain Entity -> Service -> Repository)"
                + " keeps the Domain layer pure and hides the jOOQ persistence adapter behind"
                + " Spring-injected interfaces, never depended on directly. The read flow, either"
                + " the direct Controller -> QueryRepository (jOOQ) -> ReadDto variant or the"
                + " Controller -> Service -> QueryRepository (jOOQ) -> ReadDto variant used when"
                + " the read needs extra orchestration, is a documented exception that deliberately"
                + " skips the Domain layer: Persistence may implement the module's Query interface"
                + " and project straight into ReadDtos, and a Service may consume/return those same"
                + " ReadDtos, since no business invariant needs protecting on pure reads. Layers"
                + " are matched by package pattern across all of core.modules, so this rule is"
                + " enforced on any module, present or future, without needing to be listed here"
                + " explicitly")
        .check(importedClasses);
  }
}
