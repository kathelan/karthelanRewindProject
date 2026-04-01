package pl.kathelan.dbperf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("pl.kathelan.dbperf.entity")
@EnableJpaRepositories("pl.kathelan.dbperf.repository")
public class DbPerfTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbPerfTestApplication.class, args);
    }
}
