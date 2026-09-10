package com.thriveerp.thriveERP.core.domain.user;

import java.util.Optional;
import java.util.UUID;

/**
 * The persistence port for users — analogous to EngineConnectorPort in
 * ARCHITECTURE.md §4. core-app codes against this interface only;
 * adapter-persistence provides the implementation.
 */
public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsernameOrEmail(String username, String email);
}
