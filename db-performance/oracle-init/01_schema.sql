CREATE TABLE auth_process_oracle (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR2(36)  NOT NULL,
    auth_method VARCHAR2(20)  NOT NULL,
    status      VARCHAR2(20)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL
);

CREATE OR REPLACE VIEW active_auth_processes_v AS
    SELECT id, user_id, auth_method, status, created_at
    FROM auth_process_oracle
    WHERE status = 'INITIATED';

INSERT INTO auth_process_oracle (user_id, auth_method, status, created_at) VALUES ('user-001', 'PUSH', 'INITIATED',  TIMESTAMP '2026-04-01 09:00:00');
INSERT INTO auth_process_oracle (user_id, auth_method, status, created_at) VALUES ('user-002', 'PUSH', 'COMPLETED',  TIMESTAMP '2026-04-01 09:01:00');
INSERT INTO auth_process_oracle (user_id, auth_method, status, created_at) VALUES ('user-003', 'SMS',  'INITIATED',  TIMESTAMP '2026-04-01 09:02:00');
INSERT INTO auth_process_oracle (user_id, auth_method, status, created_at) VALUES ('user-004', 'SMS',  'PUSH_SENT',  TIMESTAMP '2026-04-01 09:03:00');
INSERT INTO auth_process_oracle (user_id, auth_method, status, created_at) VALUES ('user-005', 'PUSH', 'INITIATED',  TIMESTAMP '2026-04-01 09:04:00');

COMMIT;
