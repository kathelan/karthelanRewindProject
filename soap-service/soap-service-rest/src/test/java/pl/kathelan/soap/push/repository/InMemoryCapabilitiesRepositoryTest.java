package pl.kathelan.soap.push.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCapabilitiesRepositoryTest {

    private InMemoryCapabilitiesRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCapabilitiesRepository();

        repository.save(UserCapabilities.builder()
                .userId("user-push")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());

        repository.save(UserCapabilities.builder()
                .userId("user-multi")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH, AuthMethod.SMS))
                .devices(List.of())
                .build());

        repository.save(UserCapabilities.builder()
                .userId("user-inactive")
                .active(false)
                .accountStatus(AccountStatus.SUSPENDED)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());
    }

    @Test
    void shouldReturnCapabilitiesForActiveUser() {
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

    @Test
    void shouldOverwriteExistingUserOnSave() {
        UserCapabilities updated = UserCapabilities.builder()
                .userId("user-push")
                .active(false)
                .accountStatus(AccountStatus.SUSPENDED)
                .authMethods(List.of())
                .devices(List.of())
                .build();

        repository.save(updated);

        Optional<UserCapabilities> result = repository.findByUserId("user-push");
        assertThat(result).isPresent();
        assertThat(result.get().isActive()).isFalse();
    }
}
