package pl.kathelan.auth.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kathelan.auth.service.AuthProcessSchedulerService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthProcessSchedulerTest {

    @Mock
    private AuthProcessSchedulerService schedulerService;

    private AuthProcessScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuthProcessScheduler(schedulerService);
    }

    @Test
    void pollPushStatuses_delegatesToService() {
        scheduler.pollPushStatuses();

        verify(schedulerService).pollAndUpdatePushStatuses();
    }

    @Test
    void expireOverdue_delegatesToService() {
        scheduler.expireOverdue();

        verify(schedulerService).expireOverdueProcesses();
    }
}