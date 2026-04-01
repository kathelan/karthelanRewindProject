package pl.kathelan.dbperf.oracle;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty("oracle.datasource.url")
public class ActiveProcessService {

    private final OracleViewRepository repository;

    public ActiveProcessService(OracleViewRepository repository) {
        this.repository = repository;
    }

    public List<ActiveProcessDto> findActive() {
        return repository.findAllByOrderByCreatedAt().stream()
                .map(e -> new ActiveProcessDto(e.getId(), e.getUserId(), e.getAuthMethod(), e.getStatus(), e.getCreatedAt()))
                .toList();
    }
}
