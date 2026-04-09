package pl.kathelan.soap.user.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAlreadyExistsExceptionTest {

    @Test
    void shouldContainEmailInMessage() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("test@example.com");

        assertThat(ex.getMessage()).contains("test@example.com");
    }

    @Test
    void shouldBeRuntimeException() {
        assertThat(new UserAlreadyExistsException("x@x.com"))
                .isInstanceOf(RuntimeException.class);
    }
}
