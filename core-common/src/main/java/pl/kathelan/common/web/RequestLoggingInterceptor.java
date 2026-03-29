package pl.kathelan.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

public class RequestLoggingInterceptor implements HandlerInterceptor {

    static final String START_TIME_ATTR = RequestLoggingInterceptor.class.getName() + ".startTime";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        log.debug("→ {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime == null) return;

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        int status = response.getStatus();

        if (ex != null || status >= 500) {
            log.warn("← {} {} | {}ms | error: {}", status, request.getRequestURI(), elapsedMs,
                    ex != null ? ex.getMessage() : "server error");
        } else if (status >= 400) {
            log.warn("← {} {} | {}ms", status, request.getRequestURI(), elapsedMs);
        } else {
            log.debug("← {} {} | {}ms", status, request.getRequestURI(), elapsedMs);
        }
    }
}