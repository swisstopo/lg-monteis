package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import ch.swisstopo.monteis.core.CoreApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Repository;

class CqrsTaxonomyTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(CoreApplication.class);

  @Test
  void domain_classes_must_stay_pure() {
    noClasses()
        .that()
        .resideInAnyPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..web..", "..jooq..")
        .because(
            "the write flow's Domain Entity must stay free of infrastructure concerns - it is"
                + " translated to/from by the Mapper (web) and Repository (jooq), never the"
                + " other way around")
        .check(importedClasses);
  }

  @Test
  void jooq_generated_records_must_stay_behind_the_persistence_adapter() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..jooq..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..jooq.generated..")
        .because(
            "Repositories are responsible for mapping between the Domain/DTOs and the jOOQ"
                + " database structure; generated records must not leak into domain, service or"
                + " web code")
        .check(importedClasses);
  }

  @Test
  void web_dto_classes_must_have_suffix_dto() {
    classes()
        .that()
        .resideInAnyPackage("..web.dto..")
        .should()
        .haveSimpleNameEndingWith("Dto")
        .because("write/read DTOs must be clearly identifiable at the API boundary")
        .check(importedClasses);
  }

  @Test
  void mapstruct_mappers_must_have_suffix_mapper() {
    classes()
        .that()
        .areAnnotatedWith(Mapper.class)
        .should()
        .haveSimpleNameEndingWith("Mapper")
        .check(importedClasses);
  }

  @Test
  void feature_modules_must_be_free_of_cycles() {
    slices()
        .matching("..core.modules.(*)..")
        .should()
        .beFreeOfCycles()
        .because(
            "modules are independent vertical slices (write-flow modules with a Domain, or"
                + " read-flow modules skipping it) and must not become entangled")
        .check(importedClasses);
  }

  @Test
  void query_only_repositories_must_have_suffix_query_repository() {
    classes()
        .that()
        .areAnnotatedWith(Repository.class)
        .and()
        .resideInAnyPackage("..overview..")
        .should()
        .haveSimpleNameEndingWith("QueryRepository")
        .because(
            "the read flow's repository is suffixed QueryRepository to visually signal that no"
                + " aggregate root/Domain Entity is returned")
        .check(importedClasses);
  }

  @Test
  void service_layer_must_stay_free_of_security_concerns() {
    noClasses()
        .that()
        .resideInAnyPackage("..service..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.security..")
        .because(
            "the business/service layer must stay 100% free of permission checks; security is"
                + " enforced at the system's edges (filters, jOOQ listeners), not inside business"
                + " code")
        .check(importedClasses);
  }
}
