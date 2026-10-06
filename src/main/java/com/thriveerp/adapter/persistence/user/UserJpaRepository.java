package com.thriveerp.adapter.persistence.user;

import com.thriveerp.core.domain.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByUsername(String username);
    Optional<UserJpaEntity> findByEmail(String email);
    boolean existsByUsernameOrEmail(String username, String email);
    boolean existsByRole(Role role);
}
