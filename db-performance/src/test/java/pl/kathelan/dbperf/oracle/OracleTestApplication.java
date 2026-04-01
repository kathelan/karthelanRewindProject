package pl.kathelan.dbperf.oracle;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;

/**
 * Slim Spring Boot context for Oracle integration tests.
 * Excludes PostgreSQL / JPA auto-configuration — only Oracle beans are loaded.
 */
@SpringBootApplication(
        scanBasePackages = "pl.kathelan.dbperf.oracle",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                JpaRepositoriesAutoConfiguration.class
        }
)
public class OracleTestApplication {
}
