package com.thriveerp.adapter.bootstrap;

import com.thriveerp.core.app.user.AuthService;
import com.thriveerp.core.domain.user.Role;
import com.thriveerp.core.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock AuthService authService;

    @Test
    void run_doesNothing_whenNoBootstrapConfigIsSet() {
        new AdminBootstrapRunner(authService, "", "", "").run(null);

        verifyNoInteractions(authService);
    }

    @Test
    void run_createsAdmin_whenAllThreeValuesAreSet() {
        User admin = new User(UUID.randomUUID(), "root", "root@example.com", "hash",
                Role.ADMIN, Instant.now(), Instant.now());
        when(authService.ensureAdmin("root", "root@example.com", "password123"))
                .thenReturn(Optional.of(admin));

        new AdminBootstrapRunner(authService, "root", "root@example.com", "password123").run(null);

        verify(authService).ensureAdmin("root", "root@example.com", "password123");
    }

    @Test
    void run_failsFast_whenOnlySomeValuesAreSet() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(authService, "root", "", "password123");

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(authService);
    }

    @Test
    void run_failsFast_whenPasswordIsTooShort() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(authService, "root", "root@example.com", "short");

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(authService);
    }
}
