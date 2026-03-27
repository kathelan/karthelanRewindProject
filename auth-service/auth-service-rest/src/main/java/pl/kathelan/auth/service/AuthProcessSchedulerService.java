package pl.kathelan.auth.service;

public interface AuthProcessSchedulerService {
    void pollAndUpdatePushStatuses();
}