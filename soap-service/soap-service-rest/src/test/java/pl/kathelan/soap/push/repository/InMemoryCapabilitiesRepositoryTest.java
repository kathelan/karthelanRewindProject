package pl.kathelan.soap.push.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryCapabilitiesRepository — unit tests for the in-memory user capabilities store.
 *
 * <p>Verifies the contract of {@link InMemoryCapabilitiesRepository}: lookup by userId,
 * handling of unknown users, preservation of stored data, and upsert (overwrite) semantics.
 * Each test uses a freshly seeded repository for full isolation.
 */
@DisplayName("InMemoryCapabilitiesRepository — in-memory capabilities store behaviour")
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

    // ===== findByUserId =====

    @Nested
    @DisplayName("findByUserId — retrieving capabilities for a user")
    class FindByUserId {

        /**
         * Active user: an existing active user must be found with the correct active flag
         * and at least the PUSH auth method present.
         */
        @Test
        @DisplayName("returns capabilities with active=true and PUSH method for an active user")
        void shouldReturnCapabilitiesForActiveUser() {
            Optional<UserCapabilities> result = repository.findByUserId("user-push");

            assertThat(result).isPresent();
            assertThat(result.get().isActive()).isTrue();
            assertThat(result.get().getAuthMethods()).contains(AuthMethod.PUSH);
        }

        /**
         * Multiple auth methods: a user enrolled in both PUSH and SMS must have exactly
         * those two methods in the result. Order is not significant.
         */
        @Test
        @DisplayName("returns exactly PUSH and SMS auth methods for a user enrolled in both")
        void shouldReturnCapabilitiesForUserWithMultipleMethods() {
            Optional<UserCapabilities> result = repository.findByUserId("user-multi");

            assertThat(result).isPresent();
            assertThat(result.get().getAuthMethods())
                    .containsExactlyInAnyOrder(AuthMethod.PUSH, AuthMethod.SMS);
        }

        /**
         * Inactive user: a suspended user must still be found in the store — the repository
         * must not filter by active status; that is the service layer's responsibility.
         */
        @Test
        @DisplayName("returns capabilities with active=false for a suspended user")
        void shouldReturnInactiveUserCapabilities() {
            Optional<UserCapabilities> result = repository.findByUserId("user-inactive");

            assertThat(result).isPresent();
            assertThat(result.get().isActive()).isFalse();
        }

        /**
         * Unknown user: querying a userId that was never saved must return
         * {@code Optional.empty()} — not null and not an exception.
         */
        @Test
        @DisplayName("returns Optional.empty for a userId that was never saved")
        void shouldReturnEmptyForUnknownUser() {
            Optional<UserCapabilities> result = repository.findByUserId("unknown-user");

            assertThat(result).isEmpty();
        }

        /**
         * Field preservation: the stored userId must survive the save/load round-trip
         * without modification.
         */
        @Test
        @DisplayName("preserves the original userId after save and retrieval")
        void shouldPreserveUserIdInResult() {
            Optional<UserCapabilities> result = repository.findByUserId("user-push");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo("user-push");
        }
    }

    // ===== save (upsert) =====

    @Nested
    @DisplayName("save — upsert semantics (overwrite existing entry)")
    class Save {

        /**
         * Upsert behaviour: saving a new {@link UserCapabilities} for a userId that already exists
         * must overwrite the previous entry. Subsequent lookups must return the new state.
         * This is critical for the simulator which re-seeds capabilities before each e2e run.
         */
        @Test
        @DisplayName("overwrites the existing entry when saving with the same userId")
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
}
