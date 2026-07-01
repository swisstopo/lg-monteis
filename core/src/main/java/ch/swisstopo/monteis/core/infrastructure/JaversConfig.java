package ch.swisstopo.monteis.core.infrastructure;

import javax.sql.DataSource;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.repository.sql.ConnectionProvider;
import org.javers.repository.sql.DialectName;
import org.javers.repository.sql.JaversSqlRepository;
import org.javers.repository.sql.SqlRepositoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceUtils;

@Configuration
public class JaversConfig {

  @Bean
  public ConnectionProvider javersConnectionProvider(DataSource dataSource) {
    // DataSourceUtils ensures JaVers shares the active transaction with jOOQ!
    return () -> DataSourceUtils.getConnection(dataSource);
  }

  @Bean
  public JaversSqlRepository javersSqlRepository(ConnectionProvider connectionProvider) {
    return SqlRepositoryBuilder.sqlRepository()
        .withConnectionProvider(connectionProvider)
        .withDialect(DialectName.POSTGRES)
        // We disable this programmatically since you are using Flyway scripts!
        .withSchemaManagementEnabled(false)
        .build();
  }

  @Bean
  public Javers javers(JaversSqlRepository sqlRepository) {
    return JaversBuilder.javers().registerJaversRepository(sqlRepository).build();
  }
}
