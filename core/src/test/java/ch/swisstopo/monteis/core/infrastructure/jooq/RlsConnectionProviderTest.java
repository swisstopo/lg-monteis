package ch.swisstopo.monteis.core.infrastructure.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jooq.ConnectionProvider;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The RLS session values follow {@code AccessPolicy}: the read-all and write-all flags and the
 * readable and editable experiment ids, transaction-local and fail closed (BR3.5-BR3.7).
 */
@ExtendWith(MockitoExtension.class)
class RlsConnectionProviderTest {

  private static final UUID EXPERIMENT_A = UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_B = UUID.fromString("00000000-0000-7000-8000-000000000302");
  private static final UUID EXPERIMENT_C = UUID.fromString("00000000-0000-7000-8000-000000000303");

  @Mock private ConnectionProvider delegate;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;

  private RlsConnectionProvider provider;

  @BeforeEach
  void setUp() {
    provider = new RlsConnectionProvider(delegate);
    when(delegate.acquire()).thenReturn(connection);
  }

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_set_both_all_experiments_flags_and_no_ids_for_an_admin() throws SQLException {
    RlsSettings settings = acquireWithin(SecurityContextTestSupport::runAsAdmin);

    assertEquals(new RlsSettings("true", "true", "", ""), settings);
  }

  @Test
  void should_set_both_all_experiments_flags_and_no_ids_for_a_global_editor() throws SQLException {
    RlsSettings settings = acquireWithin(SecurityContextTestSupport::runAsGlobalEditor);

    assertEquals(new RlsSettings("true", "true", "", ""), settings);
  }

  @Test
  void should_set_sorted_read_and_write_ids_for_a_scoped_editor() throws SQLException {
    RlsSettings settings =
        acquireWithin(
            action ->
                SecurityContextTestSupport.runAsUser(
                    List.of(EXPERIMENT_B, EXPERIMENT_A), List.of(EXPERIMENT_A), action));

    assertEquals(
        new RlsSettings(
            "false", "false", EXPERIMENT_A + "," + EXPERIMENT_B, EXPERIMENT_A.toString()),
        settings);
  }

  @Test
  void should_drop_write_ids_that_are_not_readable() throws SQLException {
    RlsSettings settings =
        acquireWithin(
            action ->
                SecurityContextTestSupport.runAsUser(
                    List.of(EXPERIMENT_A), List.of(EXPERIMENT_A, EXPERIMENT_C), action));

    assertEquals(
        new RlsSettings("false", "false", EXPERIMENT_A.toString(), EXPERIMENT_A.toString()),
        settings);
  }

  @Test
  void should_ignore_a_write_claim_without_the_write_authority() throws SQLException {
    RlsSettings settings =
        acquireWithin(
            action ->
                SecurityContextTestSupport.runAs(
                    List.of(
                        new SimpleGrantedAuthority(MonteisAuthorities.EXPERIMENT_READ_AUTHORITY)),
                    List.of(EXPERIMENT_A),
                    List.of(EXPERIMENT_A),
                    action));

    assertEquals(new RlsSettings("false", "false", EXPERIMENT_A.toString(), ""), settings);
  }

  @Test
  void should_set_only_the_read_all_flag_for_the_system_context() throws SQLException {
    RlsSettings settings = acquireWithin(SystemSecurityContext::runAsSystem);

    assertEquals(new RlsSettings("true", "false", "", ""), settings);
  }

  @Test
  void should_fail_closed_for_an_unbound_or_foreign_authentication() throws SQLException {
    RlsSettings unbound = acquireWithin(Runnable::run);
    RlsSettings foreign =
        acquireWithin(
            action -> {
              SecurityContext context = SecurityContextHolder.createEmptyContext();
              context.setAuthentication(
                  UsernamePasswordAuthenticationToken.authenticated(
                      "x",
                      null,
                      List.of(new SimpleGrantedAuthority(MonteisAuthorities.ADMIN_AUTHORITY))));
              SecurityContextHolder.setContext(context);
              action.run();
            });

    assertEquals(new RlsSettings("false", "false", "", ""), unbound);
    assertEquals(new RlsSettings("false", "false", "", ""), foreign);
  }

  @Test
  void should_wrap_a_failure_to_set_the_context_in_a_data_access_exception() throws SQLException {
    SQLException failure = new SQLException("connection lost", "08006");
    when(connection.prepareStatement(anyString())).thenThrow(failure);

    DataAccessException thrown =
        assertThrows(
            DataAccessException.class,
            () -> SecurityContextTestSupport.runAsAdmin(provider::acquire));

    assertSame(failure, thrown.getCause());
    verifyNoInteractions(statement);
  }

  /**
   * Acquires a connection while {@code binder} has bound an authentication and returns the four
   * values written onto it.
   */
  private RlsSettings acquireWithin(Consumer<Runnable> binder) throws SQLException {
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    AtomicReference<Connection> acquired = new AtomicReference<>();

    binder.accept(() -> acquired.set(provider.acquire()));

    assertSame(connection, acquired.get());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    assertEquals(
        "SELECT set_config('app.read_all_experiments', ?, true),"
            + " set_config('app.write_all_experiments', ?, true),"
            + " set_config('app.read_experiment_ids', ?, true),"
            + " set_config('app.write_experiment_ids', ?, true)",
        sql.getValue());

    ArgumentCaptor<Integer> index = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
    verify(statement, times(4)).setString(index.capture(), value.capture());
    verify(statement).execute();
    verify(statement).close();

    Map<Integer, String> parameters = new HashMap<>();
    for (int i = 0; i < index.getAllValues().size(); i++) {
      parameters.put(index.getAllValues().get(i), value.getAllValues().get(i));
    }
    clearInvocations(connection, statement);
    return new RlsSettings(
        parameters.get(1), parameters.get(2), parameters.get(3), parameters.get(4));
  }

  private record RlsSettings(
      String readAllExperiments, String writeAllExperiments, String readIds, String writeIds) {}
}
