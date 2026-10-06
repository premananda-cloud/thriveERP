package com.thriveerp.core.app.user;

import com.thriveerp.core.domain.user.PasswordEncoderPort;
import com.thriveerp.core.domain.user.Role;
import com.thriveerp.core.domain.user.TokenServicePort;
import com.thriveerp.core.domain.user.User;
import com.thriveerp.core.domain.user.UserRepositoryPort;
import com.thriveerp.core.domain.user.exception.DuplicateUserException;
import com.thriveerp.core.domain.user.exception.InvalidCredentialsException;
import com.thriveerp.core.domain.user.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * All three ports are mocked — no Spring context, no database, no real
 * JWT/hashing library. This is the core business logic; it should be the
 * fastest-running and most thorough test class in the project.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepositoryPort userRepository;
    @Mock PasswordEncoderPort passwordEncoder;
    @Mock TokenServicePort tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void register_savesNewCustomer_whenUsernameAndEmailAreUnique() {
        when(userRepository.existsByUsernameOrEmail("alice", "alice@example.com")).thenReturn(false);
        when(passwordEncoder.hash("password123")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register("alice", "alice@example.com", "password123");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(result.getPasswordHash()).isEqualTo("hashed-value");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsDuplicateUserException_whenUsernameOrEmailAlreadyExists() {
        when(userRepository.existsByUsernameOrEmail("alice", "alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("alice", "alice@example.com", "password123"))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_returnsToken_whenCredentialsAreValid() {
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hashed-value",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-value")).thenReturn(true);
        when(tokenService.issueToken(user)).thenReturn("signed.jwt.token");

        String token = authService.login("alice", "password123");

        assertThat(token).isEqualTo("signed.jwt.token");
    }

    @Test
    void login_throwsInvalidCredentialsException_whenUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenService);
    }

    @Test
    void login_throwsInvalidCredentialsException_whenPasswordIsWrong() {
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hashed-value",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-value")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenService);
    }

    @Test
    void login_givesTheSameErrorMessage_forMissingUserAndWrongPassword() {
        // Deliberate security property (not incidental): a caller shouldn't
        // be able to tell "no such user" apart from "wrong password".
        // Asserted directly so a future refactor can't silently break it.
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        String messageForMissingUser = captureMessage(() -> authService.login("ghost", "x"));

        User user = new User(UUID.randomUUID(), "alice", "a@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        String messageForWrongPassword = captureMessage(() -> authService.login("alice", "wrong"));

        assertThat(messageForMissingUser).isEqualTo(messageForWrongPassword);
    }

    private String captureMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected InvalidCredentialsException but none was thrown");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }

    @Test
    void changeRole_updatesAndSavesUser_whenUserExists() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.changeRole(id, Role.ADMIN);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void changeRole_throwsUserNotFoundException_whenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changeRole(id, Role.ADMIN))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    // ---- ensureAdmin (first-admin bootstrap) ----

    @Test
    void ensureAdmin_doesNothing_whenAnAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        Optional<User> result = authService.ensureAdmin("root", "root@example.com", "password123");

        assertThat(result).isEmpty();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void ensureAdmin_createsAdminAccount_whenNoAdminExistsAndUsernameIsFree() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByUsername("root")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.hash("password123")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.ensureAdmin("root", "root@example.com", "password123").orElseThrow();

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getUsername()).isEqualTo("root");
        assertThat(result.getPasswordHash()).isEqualTo("hashed-value");
    }

    @Test
    void ensureAdmin_promotesExistingUser_whenUsernameAlreadyRegistered() {
        User existing = new User(UUID.randomUUID(), "root", "root@example.com", "old-hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.ensureAdmin("root", "root@example.com", "ignored-password").orElseThrow();

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getPasswordHash()).isEqualTo("old-hash"); // password untouched
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void ensureAdmin_throwsDuplicateUserException_whenEmailBelongsToAnotherUser() {
        User other = new User(UUID.randomUUID(), "someone", "root@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByUsername("root")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> authService.ensureAdmin("root", "root@example.com", "password123"))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }
}
