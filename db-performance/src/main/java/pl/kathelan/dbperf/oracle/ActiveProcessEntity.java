package pl.kathelan.dbperf.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "active_auth_processes_v")
public class ActiveProcessEntity {

    @Id
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "auth_method")
    private String authMethod;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getAuthMethod() { return authMethod; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
