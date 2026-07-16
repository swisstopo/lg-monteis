package ch.swisstopo.monteis.core.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import ch.swisstopo.monteis.core.CoreApplication;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jooq.UpdatableRecord;
import org.junit.jupiter.api.Test;

class OptimisticLockingAndAuditingTest {

  private static final JavaClasses importedClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/generated/"))
          .importPackagesOf(CoreApplication.class);

  @Test
  void audit_changes_methods_must_return_an_auditable_type() {
    methods()
        .that()
        .areAnnotatedWith(AuditChanges.class)
        .should()
        .haveRawReturnType(assignableTo(Auditable.class))
        .because(
            "JaversAuditAspect#auditReturnValue binds to the returned value by its Auditable"
                + " type via Spring AOP @AfterReturning; if the return type is not assignable to"
                + " Auditable, the advice silently never fires and the change is never audited")
        .check(importedClasses);
  }

  @Test
  void no_classes_should_use_the_generic_jooq_record_store_method() {
    noClasses()
        .should()
        .callMethodWhere(
            target(
                describe(
                    "UpdatableRecord#store()",
                    (AccessTarget accessTarget) ->
                        accessTarget.getName().equals("store")
                            && accessTarget.getOwner().isAssignableTo(UpdatableRecord.class))))
        .because(
            "record.store() implicitly chooses INSERT or UPDATE and bypasses the explicit"
                + " insert()/update() split that optimistic locking relies on")
        .check(importedClasses);
  }
}
