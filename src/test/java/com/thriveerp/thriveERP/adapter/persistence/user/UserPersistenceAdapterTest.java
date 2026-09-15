package com.thriveerp.thriveERP.adapter.persistence.user;

import com.thriveerp.thriveERP.core.domain.user.Role;
import com.thriveerp.thriveERP.core.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the load-then-update logic specifically — the one part of this
 * adapter that isn't obvious from reading the port interface, and the whole
 * reason save() isn't a naive "build a fresh entity and save it".
 * See the comment on UserPersistenceAdapter#save for the full explanation
 * (app-assigned UUIDs + @Version-based isNew() detection).
 */
@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

    @Mock UserJpaRepository jpaRepository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(jpaRepository);
    }

    @Test
    void save_buildsFreshEntity_whenUserDoesNotExistYet() {
        UUID id = UUID.randomUUID();
        User newUser = new User(id, "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());
        when(jpaRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adapter.save(newUser);

        ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getVersion()).isNull(); // fresh entity, never persisted before
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void save_mutatesTheLoadedEntityInPlace_ratherThanConstructingAFreshOne() {
        UUID id = UUID.randomUUID();
        Instant originalCreatedAt = Instant.now().minusSeconds(3600);
        UserJpaEntity existingEntity = new UserJpaEntity(id, "alice", "alice@example.com",
                "old-hash", Role.CUSTOMER, originalCreatedAt, originalCreatedAt);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(existingEntity));
        when(jpaRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        User domainUpdate = new User(id, "alice", "alice@example.com", "old-hash",
                Role.ADMIN, originalCreatedAt, Instant.now());
        adapter.save(domainUpdate);

        ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        // Same object instance findById returned — proves we mutated the
        // loaded entity (which carries its real @Version) rather than
        // building a new one, which is what keeps optimistic locking working.
        assertThat(captor.getValue()).isSameAs(existingEntity);
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
    }
}
