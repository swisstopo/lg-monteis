package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import ch.swisstopo.monteis.core.CoreApplication;
import ch.swisstopo.monteis.core.itconfig.IT;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class CodingStandardsTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(CoreApplication.class);

  private static final JavaClasses allClasses =
      new ClassFileImporter().importPackagesOf(CoreApplication.class);

  @Test
  void test_classes_should_reside_in_the_same_package_as_implementation() {
    GeneralCodingRules.testClassesShouldResideInTheSamePackageAsImplementation().check(allClasses);
  }

  @Test
  void mockito_test_classes_should_have_suffix_Test() {
    DescribedPredicate<JavaClass> extendedWithMockito =
        describe(
            "annotated with @ExtendWith(MockitoExtension.class)",
            clazz ->
                clazz.isAnnotatedWith(ExtendWith.class)
                    && Arrays.asList(clazz.getAnnotationOfType(ExtendWith.class).value())
                        .contains(MockitoExtension.class));

    classes().that(extendedWithMockito).should().haveSimpleNameEndingWith("Test").check(allClasses);
  }

  @Test
  void integration_test_classes_should_have_suffix_IT() {
    classes()
        .that()
        .areAnnotatedWith(IT.class)
        .should()
        .haveSimpleNameEndingWith("IT")
        .check(allClasses);
  }

  @Test
  void no_classes_should_access_standard_streams() {
    GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(importedClasses);
  }

  @Test
  void no_classes_should_throw_generic_exceptions() {
    GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(importedClasses);
  }

  @Test
  void no_classes_should_use_deprecated_apis() {
    GeneralCodingRules.DEPRECATED_API_SHOULD_NOT_BE_USED.check(importedClasses);
  }

  @Test
  void no_classes_should_use_field_injection() {
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(importedClasses);
  }

  @Test
  void configuration_classes_should_have_suffix_Config() {
    classes()
        .that()
        .resideInAnyPackage("..core.infrastructure..")
        .and()
        .areAnnotatedWith(Configuration.class)
        .should()
        .haveSimpleNameEndingWith("Config")
        .check(importedClasses);
  }

  @Test
  void repositories_should_have_suffix_Repository() {
    classes()
        .that()
        .areAnnotatedWith(Repository.class)
        .should()
        .haveSimpleNameEndingWith("Repository")
        .check(importedClasses);
  }

  @Test
  void only_one_centralized_rest_controller_advice_must_exist() {
    classes()
        .that()
        .areAnnotatedWith(RestControllerAdvice.class)
        .should()
        .haveSimpleName("GlobalErrorControllerAdvice")
        .because(
            "GlobalErrorControllerAdvice is the single, centralized place where backend"
                + " exceptions are translated into the ErrorDto API contract; a second"
                + " @RestControllerAdvice would fragment error handling")
        .check(importedClasses);
  }
}
