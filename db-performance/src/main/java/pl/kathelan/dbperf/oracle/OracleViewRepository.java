package pl.kathelan.dbperf.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OracleViewRepository extends JpaRepository<ActiveProcessEntity, Long> {

    List<ActiveProcessEntity> findAllByOrderByCreatedAt();
}
