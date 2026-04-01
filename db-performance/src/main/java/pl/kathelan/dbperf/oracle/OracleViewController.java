package pl.kathelan.dbperf.oracle;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/oracle")
@ConditionalOnProperty("oracle.datasource.url")
public class OracleViewController {

    private final ActiveProcessService service;

    public OracleViewController(ActiveProcessService service) {
        this.service = service;
    }

    @GetMapping("/active-processes")
    public List<ActiveProcessDto> getActiveProcesses() {
        return service.findActive();
    }
}
