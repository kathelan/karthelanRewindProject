package pl.kathelan.soap.push.repository;

import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.Optional;

public interface CapabilitiesRepository {

    Optional<UserCapabilities> findByUserId(String userId);

    void save(UserCapabilities capabilities);
}