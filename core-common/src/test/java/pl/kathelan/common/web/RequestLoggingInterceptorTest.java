package pl.kathelan.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;


/**
 * RequestLoggingInterceptor — Spring HandlerInterceptor logujący każde żądanie HTTP.
 *
 * Działa w dwóch fazach:
 *   1. preHandle  — zapisuje timestamp startu w atrybucie requestu, loguje "→ METHOD /path"
 *   2. afterCompletion — oblicza czas wykonania i loguje "← STATUS /path | Xms"
 *      - status 2xx/3xx → DEBUG
 *      - status 4xx     → WARN
 *      - status 5xx lub wyjątek → WARN z komunikatem błędu
 *
 * Musi zwrócić true z preHandle — inaczej Spring zatrzyma łańcuch filtrów.
 * Bezpieczny gdy preHandle nie zostało wywołane (brak atrybutu startu → nic nie loguje).
 */
@DisplayName("RequestLoggingInterceptor")
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

    @Nested
    @DisplayName("preHandle — przed wykonaniem żądania")
    class PreHandle {

        @Test
        @DisplayName("zapisuje timestamp startu w atrybucie requestu (potrzebny do obliczenia czasu)")
        void storesStartTimeInRequestAttribute() {
            request.setMethod("POST");
            request.setRequestURI("/process/init");

            long before = System.nanoTime();
            interceptor.preHandle(request, response, new Object());
            long after = System.nanoTime();

            Long startTime = (Long) request.getAttribute(RequestLoggingInterceptor.START_TIME_ATTR);
            assertThat(startTime).isBetween(before, after);
        }

        @Test
        @DisplayName("zawsze zwraca true — żeby Spring kontynuował obsługę żądania")
        void alwaysReturnsTrue() {
            request.setMethod("GET");
            request.setRequestURI("/auth/capabilities/user1");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("afterCompletion — po wykonaniu żądania")
    class AfterCompletion {

        @Test
        @DisplayName("nie rzuca wyjątku gdy preHandle nie było wywołane (brak timestampu startu)")
        void doesNotThrowWhenStartTimeMissing() {
            response.setStatus(200);
            Object handler = new Object();

            // Scenariusz: interceptor dołączony po starcie aplikacji, preHandle pominięte
            assertThatNoException().isThrownBy(
                    () -> interceptor.afterCompletion(request, response, handler, null));
        }

        @Test
        @DisplayName("nie rzuca wyjątku gdy żądanie zakończyło się błędem 500")
        void doesNotThrowOnServerError() {
            request.setAttribute(RequestLoggingInterceptor.START_TIME_ATTR, System.nanoTime());
            response.setStatus(500);
            Object handler = new Object();
            RuntimeException serverError = new RuntimeException("błąd serwera");

            // Interceptor loguje błąd, ale sam nie może rzucać — zatrzymałby pipeline Spring MVC
            assertThatNoException().isThrownBy(
                    () -> interceptor.afterCompletion(request, response, handler, serverError));
        }
    }
}
