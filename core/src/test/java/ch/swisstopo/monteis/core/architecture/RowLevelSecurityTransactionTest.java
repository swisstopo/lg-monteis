package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import ch.swisstopo.monteis.core.CoreApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class RowLevelSecurityTransactionTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(CoreApplication.class);

  private static final DescribedPredicate<JavaClass> QUERY_INTERFACE =
      resideInAPackage("..core.modules..query..");

  private static final DescribedPredicate<JavaMethod> IMPLEMENTS_A_QUERY_METHOD =
      describe(
          "implements a method declared by a Query interface (the CQRS read contract)",
          method ->
              method.getOwner().getAllRawInterfaces().stream()
                  .filter(QUERY_INTERFACE::test)
                  .flatMap(iface -> iface.getMethods().stream())
                  .anyMatch(
                      interfaceMethod ->
                          interfaceMethod.getName().equals(method.getName())
                              && interfaceMethod
                                  .getRawParameterTypes()
                                  .equals(method.getRawParameterTypes())));

  private static final DescribedPredicate<JavaMethod> RUNS_INSIDE_A_SPRING_TRANSACTION =
      describe(
          "be annotated with @Transactional, or be declared in a class annotated with"
              + " @Transactional",
          method ->
              method.isAnnotatedWith(Transactional.class)
                  || method.getOwner().isAnnotatedWith(Transactional.class));

  @Test
  void read_query_methods_must_run_inside_a_spring_transaction() {
    methods()
        .that(IMPLEMENTS_A_QUERY_METHOD)
        .should(ArchCondition.from(RUNS_INSIDE_A_SPRING_TRANSACTION))
        .because(
            "per arc42 ch. 8 'Core-API Datenzugriff & Sicherheit', RlsConnectionProvider writes"
                + " the current Authentication onto the DB connection transaction-locally on"
                + " every connection acquisition, and native Postgres RLS policies only ever see"
                + " that context for the lifetime of a Spring transaction; every read query"
                + " (a Query interface implementation) must therefore run inside one via"
                + " @Transactional(readOnly = true), declared on the method itself or on its"
                + " owning class")
        .check(importedClasses);
  }
}
