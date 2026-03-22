package pl.kathelan.soap.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTest {

    @Test
    void shouldContainIdInMessage() {
        UserNotFoundException ex = new UserNotFoundException("abc-123");

        assertThat(ex.getMessage()).contains("abc-123");
    }

    @Test
    void shouldBeRuntimeException() {
        assertThat(new UserNotFoundException("id"))
                .isInstanceOf(RuntimeException.class);
    }
}
