package pl.kathelan.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingInterceptorTest {

    private RequestLoggingInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new RequestLoggingInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void preHandle_storesStartTimeInRequestAttribute() {
        request.setMethod("POST");
        request.setRequestURI("/process/init");

        long before = System.nanoTime();
        interceptor.preHandle(request, response, new Object());
        long after = System.nanoTime();

        Long startTime = (Long) request.getAttribute(RequestLoggingInterceptor.START_TIME_ATTR);
        assertThat(startTime).isBetween(before, after);
    }

    @Test
    void preHandle_returnsTrue() {
        request.setMethod("GET");
        request.setRequestURI("/auth/capabilities/user1");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void afterCompletion_doesNotThrowWhenStartTimeMissing() {
        response.setStatus(200);

        // no preHandle called — attribute missing
        interceptor.afterCompletion(request, response, new Object(), null);
        // no exception = pass
    }

    @Test
    void afterCompletion_doesNotThrowOnException() {
        request.setAttribute(RequestLoggingInterceptor.START_TIME_ATTR, System.nanoTime());
        response.setStatus(500);

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));
        // no exception = pass
    }
}