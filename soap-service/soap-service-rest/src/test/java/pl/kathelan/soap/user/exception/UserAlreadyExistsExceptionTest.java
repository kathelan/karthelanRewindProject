package pl.kathelan.soap.user.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserAlreadyExistsException — unit tests for the domain exception thrown on duplicate email.
 *
 * <p>Verifies the two invariants of this exception:
 * <ul>
 *   <li>The duplicate email is embedded in the message so logs are actionable.</li>
 *   <li>The exception is an unchecked {@link RuntimeException} — callers do not need to
 *       declare or catch it explicitly.</li>
 * </ul>
 */
@DisplayName("UserAlreadyExistsException — domain exception for duplicate email")
class UserAlreadyExistsExceptionTest {

    @Nested
    @DisplayName("message — contains the duplicate email for actionable logging")
    class Message {

        /**
         * The message must include the email that caused the conflict so that
         * log entries and SOAP error responses can identify the duplicate without
         * requiring an additional lookup.
         */
        @Test
        @DisplayName("contains the duplicate email in the exception message")
        void shouldContainEmailInMessage() {
            UserAlreadyExistsException ex = new UserAlreadyExistsException("test@example.com");

            assertThat(ex.getMessage()).contains("test@example.com");
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
            assertThat(new UserAlreadyExistsException("x@x.com"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
