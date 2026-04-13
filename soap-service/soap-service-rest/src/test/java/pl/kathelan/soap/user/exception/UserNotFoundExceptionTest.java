package pl.kathelan.soap.user.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserNotFoundException — unit tests for the domain exception thrown when a user id is not found.
 *
 * <p>Verifies the two invariants of this exception:
 * <ul>
 *   <li>The unknown id is embedded in the message so logs and SOAP error responses
 *       can identify which resource was missing.</li>
 *   <li>The exception is an unchecked {@link RuntimeException} — callers do not need to
 *       declare or catch it explicitly.</li>
 * </ul>
 */
@DisplayName("UserNotFoundException — domain exception for missing user id")
class UserNotFoundExceptionTest {

    @Nested
    @DisplayName("message — contains the missing id for actionable logging")
    class Message {

        /**
         * The message must include the id that was not found so that log entries
         * and SOAP error responses can identify the missing resource without an
         * additional query.
         */
        @Test
        @DisplayName("contains the unknown user id in the exception message")
        void shouldContainIdInMessage() {
            UserNotFoundException ex = new UserNotFoundException("abc-123");

            assertThat(ex.getMessage()).contains("abc-123");
        }
    }

    @Nested
    @DisplayName("type — is an unchecked RuntimeException")
    class Type {

        /**
         * Being a {@link RuntimeException} means the calling code (endpoints, services)
         * does not need try-catch blocks — the exception propagates naturally and is
         * handled by the global fault interceptor.
         */
        @Test
        @DisplayName("extends RuntimeException so it is unchecked")
        void shouldBeRuntimeException() {
            assertThat(new UserNotFoundException("id"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
