package ch.swisstopo.monteis.pipeline.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import ch.swisstopo.monteis.pipeline.PipelineApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class EipPipelineTaxonomyTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(PipelineApplication.class);

  @Test
  void processing_and_transformation_classes_must_follow_eip_naming_taxonomy() {
    classes()
        .that()
        .resideInAnyPackage(
            "..pipeline.transformation.processing..",
            "..pipeline.transformation.reprocessing..",
            "..pipeline.transformation.standardization..",
            "..pipeline.transformation.validation..")
        .and()
        .resideOutsideOfPackages("..cache..")
        .and()
        .areNotInterfaces()
        .and()
        .areNotEnums()
        .should()
        .haveSimpleNameEndingWith("Processor")
        .orShould()
        .haveSimpleNameEndingWith("Handler")
        .orShould()
        .haveSimpleNameEndingWith("Listener")
        .orShould()
        .haveSimpleNameEndingWith("Orchestrator")
        .orShould()
        .haveSimpleNameEndingWith("Standardizer")
        .orShould()
        .haveSimpleNameEndingWith("Converter")
        .orShould()
        .haveSimpleNameEndingWith("Translator")
        .orShould()
        .haveSimpleNameEndingWith("Validator")
        .because(
            "pipeline transformation stages must clearly indicate their EIP role (Processor,"
                + " Handler, Standardizer, etc.)")
        .check(importedClasses);
  }

  @Test
  void kafka_listeners_must_be_named_listener_and_be_components() {
    classes()
        .that()
        .containAnyMethodsThat(are(annotatedWith(KafkaListener.class)))
        .should()
        .haveSimpleNameEndingWith("Listener")
        .andShould()
        .beAnnotatedWith(Component.class)
        .andShould()
        .notBeAnnotatedWith(Service.class)
        .because(
            "classes consuming Kafka messages are infrastructure listeners, not domain services")
        .check(importedClasses);
  }

  @Test
  void external_ingress_classes_must_not_inject_publishers_or_templates() {
    noClasses()
        .that()
        .resideInAPackage("..pipeline.ingress.external..")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Publisher")
        .orShould()
        .dependOnClassesThat()
        .haveSimpleName("KafkaTemplate")
        .because(
            "external Kafka ingress listeners must use declarative @SendTo forwarding instead of"
                + " manual programmatic publishing")
        .check(importedClasses);
  }

  @Test
  void egress_adapters_must_be_publishers_and_components() {
    classes()
        .that()
        .resideInAPackage("..pipeline.egress..")
        .should()
        .haveSimpleNameEndingWith("Publisher")
        .orShould()
        .haveSimpleNameEndingWith("Sender")
        .orShould()
        .haveSimpleNameEndingWith("Adapter")
        .andShould()
        .beAnnotatedWith(Component.class)
        .andShould()
        .notBeAnnotatedWith(Service.class)
        .because("outbound integration adapters belong to infrastructure, not core business logic")
        .check(importedClasses);
  }

  @Test
  void pipeline_infrastructure_must_not_use_service_stereotype() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "..pipeline.egress..",
            "..pipeline.transformation.processing..",
            "..pipeline.transformation.reprocessing..",
            "..pipeline.transformation.standardization..",
            "..pipeline.transformation.validation..")
        .should()
        .beAnnotatedWith(Service.class)
        .orShould()
        .haveSimpleNameEndingWith("Service")
        .because(
            "the @Service stereotype and suffix are strictly reserved for transport-agnostic core"
                + " domain logic")
        .check(importedClasses);
  }
}
