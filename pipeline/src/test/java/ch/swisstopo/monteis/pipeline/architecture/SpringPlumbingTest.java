package ch.swisstopo.monteis.pipeline.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.ProxyRules.no_classes_should_directly_call_other_methods_declared_in_the_same_class_that;

import ch.swisstopo.monteis.pipeline.PipelineApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

class SpringPlumbingTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(PipelineApplication.class);

  @Test
  void no_self_invocation_of_any_proxied_methods() {
    no_classes_should_directly_call_other_methods_declared_in_the_same_class_that(is(proxied()))
        .check(importedClasses);
  }

  @Test
  void repositories_should_not_inject_other_repositories() {
    noConstructors()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Repository.class)
        .should()
        .haveRawParameterTypes(
            describe(
                "any parameter is annotated with @Repository",
                types -> types.stream().anyMatch(clazz -> clazz.isAnnotatedWith(Repository.class))))
        .because(
            "repositories must not inject other repositories; cross-aggregate data assembly belongs"
                + " in the transformation layer")
        .check(importedClasses);

    noFields()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Repository.class)
        .should()
        .haveRawType(annotatedWith(Repository.class))
        .because(
            "repositories must not inject other repositories; cross-aggregate data assembly belongs"
                + " in the transformation layer")
        .check(importedClasses);
  }

  @Test
  void repositories_should_not_define_transaction_boundaries() {
    // 1. Ensure the repository class itself is not annotated with @Transactional
    noClasses()
        .that()
        .areAnnotatedWith(Repository.class)
        .should()
        .beAnnotatedWith(Transactional.class)
        .because(
            "repositories are simple data access abstractions; transaction boundaries belong to the"
                + " service/processor layer via TransactionTemplate")
        .check(importedClasses);

    // 2. Ensure no individual methods inside the repository are annotated with @Transactional
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Repository.class)
        .should()
        .beAnnotatedWith(Transactional.class)
        .because(
            "repositories are simple data access abstractions; transaction boundaries belong to the"
                + " service/processor layer via TransactionTemplate")
        .check(importedClasses);
  }

  @Test
  void event_and_kafka_listeners_should_not_be_called_directly() {
    noClasses()
        .should()
        .callMethodWhere(
            target(annotatedWith(KafkaListener.class).or(annotatedWith(EventListener.class))))
        .check(importedClasses);
  }

  private static DescribedPredicate<AccessTarget.MethodCallTarget> proxied() {
    return describe(
        "proxied via method or class",
        target ->
            Stream.of(Transactional.class, Async.class, Cacheable.class)
                .anyMatch(a -> target.isAnnotatedWith(a) || target.getOwner().isAnnotatedWith(a)));
  }
}
