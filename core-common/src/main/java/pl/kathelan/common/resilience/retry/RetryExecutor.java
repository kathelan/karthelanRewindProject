package pl.kathelan.common.resilience.retry;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class RetryExecutor {

    public <T> T execute(Supplier<T> call, RetryConfig config) {
        Exception lastException = null;
        long delayMs = config.initialDelay().toMillis();

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                T result = call.get();
                if (attempt > 1) {
                    log.info("Retry succeeded on attempt {}/{}", attempt, config.maxAttempts());
                }
                return result;
            } catch (Exception e) {
                lastException = e;

                if (!config.shouldRetry(e)) {
                    log.debug("Exception not retryable: {}", e.getClass().getSimpleName());
                    throw rethrow(e);
                }

                if (attempt < config.maxAttempts()) {
                    log.warn("Attempt {}/{} failed: {} — retrying in {}ms",
                            attempt, config.maxAttempts(), e.getMessage(), delayMs);
                    sleep(delayMs);
                    delayMs = (long) (delayMs * config.multiplier());
                } else {
                    log.error("All {}/{} attempts failed: {}", attempt, config.maxAttempts(), e.getMessage());
                }
            }
        }

        throw rethrow(lastException);
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends RuntimeException> RuntimeException rethrow(Exception e) {
        throw (E) e;
    }
}
