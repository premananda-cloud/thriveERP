package com.thriveerp.thriveERP.adapter.persistence.user;

import com.thriveerp.thriveERP.core.domain.user.User;
import com.thriveerp.thriveERP.core.domain.user.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;

    public UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        // Deliberate: load-then-update rather than constructing a fresh
        // UserJpaEntity blindly. Our IDs are app-assigned UUIDs (not DB
        // generated), so Spring Data's default isNew() check — which looks
        // at the @Version field — would treat every save() as an insert and
        // throw a duplicate-key error on updates (e.g. role changes). Loading
        // the existing row first keeps the real @Version value intact and
        // makes optimistic locking work correctly.
        UserJpaEntity entity = jpaRepository.findById(user.getId())
                .map(existing -> {
                    existing.setUsername(user.getUsername());
                    existing.setEmail(user.getEmail());
                    existing.setPasswordHash(user.getPasswordHash());
                    existing.setRole(user.getRole());
                    existing.setUpdatedAt(user.getUpdatedAt());
                    return existing;
                })
                .orElseGet(() -> new UserJpaEntity(
                        user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash(),
                        user.getRole(), user.getCreatedAt(), user.getUpdatedAt()));

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        return jpaRepository.existsByUsernameOrEmail(username, email);
    }

    private User toDomain(UserJpaEntity e) {
        return new User(e.getId(), e.getUsername(), e.getEmail(), e.getPasswordHash(),
                e.getRole(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
