package pl.kathelan.soap.push.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCapabilitiesRepositoryTest {

    private InMemoryCapabilitiesRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCapabilitiesRepository();
    }

    @Test
    void shouldReturnCapabilitiesForSeededActiveUser() {
        Optional<UserCapabilities> result = repository.findByUserId("user-push");

        assertThat(result).isPresent();
        assertThat(result.get().isActive()).isTrue();
        assertThat(result.get().getAuthMethods()).contains(AuthMethod.PUSH);
    }

    @Test
    void shouldReturnCapabilitiesForUserWithMultipleMethods() {
        Optional<UserCapabilities> result = repository.findByUserId("user-multi");

        assertThat(result).isPresent();
        assertThat(result.get().getAuthMethods())
                .containsExactlyInAnyOrder(AuthMethod.PUSH, AuthMethod.SMS);
    }

    @Test
    void shouldReturnInactiveUserCapabilities() {
        Optional<UserCapabilities> result = repository.findByUserId("user-inactive");

        assertThat(result).isPresent();
        assertThat(result.get().isActive()).isFalse();
    }

    @Test
    void shouldReturnEmptyForUnknownUser() {
        Optional<UserCapabilities> result = repository.findByUserId("unknown-user");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPreserveUserIdInResult() {
        Optional<UserCapabilities> result = repository.findByUserId("user-push");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-push");
    }
}