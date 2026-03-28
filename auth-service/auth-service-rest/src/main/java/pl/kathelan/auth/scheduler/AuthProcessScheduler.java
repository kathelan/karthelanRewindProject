package pl.kathelan.auth.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.service.AuthProcessSchedulerService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthProcessScheduler {

    private final AuthProcessSchedulerService schedulerService;

    @Scheduled(fixedDelay = 3_000)
    public void pollPushStatuses() {
        log.debug("Running push status poll");
        schedulerService.pollAndUpdatePushStatuses();
    }

    @Scheduled(fixedDelay = 10_000)
    public void expireOverdue() {
        log.debug("Running overdue process expiry check");
        schedulerService.expireOverdueProcesses();
    }
}