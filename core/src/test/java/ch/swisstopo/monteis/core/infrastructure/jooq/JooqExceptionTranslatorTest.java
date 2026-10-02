package ch.swisstopo.monteis.core.infrastructure.jooq;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;

/**
 * The jOOQ exception translator of {@link JooqConfig} turns a write rejected by an RLS {@code WITH
 * CHECK} (SQLSTATE 42501) into {@link PermissionDeniedDataAccessException}, which the web layer
 * maps to 403 {@code access.denied} (BR3.9, FR7.3), and leaves every other error to Spring's
 * default translation.
 */
@ExtendWith(MockitoExtension.class)
class JooqExceptionTranslatorTest {

  private static final String UPDATE_SQL = "update experiments set name = ? where id = ?";

  private final ExecuteListener translator = new JooqConfig().jooqExceptionTranslator();

  @Mock private ExecuteContext context;

  @Test
  void should_translate_an_rls_rejection_to_permission_denied() {
    SQLException rejection =
        new SQLException(
            "new row violates row-level security policy for table \"experiments\"", "42501");

    DataAccessException translated = translate(rejection);

    assertInstanceOf(PermissionDeniedDataAccessException.class, translated);
    assertSame(rejection, translated.getCause());
  }

  @Test
  void should_keep_the_default_translation_for_other_class_42_errors() {
    SQLException undefinedTable =
        new SQLException("relation \"experiment\" does not exist", "42P01");

    DataAccessException translated = translate(undefinedTable);

    assertInstanceOf(BadSqlGrammarException.class, translated);
  }

  @Test
  void should_leave_non_sql_exceptions_untouched() {
    when(context.sqlException()).thenReturn(null);

    translator.exception(context);

    verify(context, never()).exception(any());
  }

  private DataAccessException translate(SQLException exception) {
    when(context.sqlException()).thenReturn(exception);
    when(context.sql()).thenReturn(UPDATE_SQL);

    translator.exception(context);

    ArgumentCaptor<RuntimeException> translated = ArgumentCaptor.forClass(RuntimeException.class);
    verify(context).exception(translated.capture());
    return assertInstanceOf(DataAccessException.class, translated.getValue());
  }
}
