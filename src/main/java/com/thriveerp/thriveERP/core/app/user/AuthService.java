package com.thriveerp.thriveERP.core.app.user;

import com.thriveerp.thriveERP.core.domain.user.PasswordEncoderPort;
import com.thriveerp.thriveERP.core.domain.user.Role;
import com.thriveerp.thriveERP.core.domain.user.TokenServicePort;
import com.thriveerp.thriveERP.core.domain.user.User;
import com.thriveerp.thriveERP.core.domain.user.UserRepositoryPort;
import com.thriveerp.thriveERP.core.domain.user.exception.DuplicateUserException;
import com.thriveerp.thriveERP.core.domain.user.exception.InvalidCredentialsException;
import com.thriveerp.thriveERP.core.domain.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * core-app: orchestration only. Knows nothing about Postgres, JWT libraries,
 * or Spring Security internals — only the ports defined in core-domain.
 * (ARCHITECTURE.md §3 — "core-app ... Knows nothing about Postgres or the ERP".
 * Same rule applies here to the auth infrastructure.)
 */
@Service
public class AuthService {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenServicePort tokenService;

    public AuthService(UserRepositoryPort userRepository,
                        PasswordEncoderPort passwordEncoder,
                        TokenServicePort tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public User register(String username, String email, String rawPassword) {
        if (userRepository.existsByUsernameOrEmail(username, email)) {
            throw new DuplicateUserException("Username or email already registered");
        }
        String hash = passwordEncoder.hash(rawPassword);
        User user = User.newRegistration(username, email, hash);
        return userRepository.save(user);
    }

    /** Returns a signed JWT on success. Deliberately vague on failure reason
     *  (bad username vs bad password) to avoid leaking which one was wrong. */
    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        return tokenService.issueToken(user);
    }

    /** "Granting privilege" per ARCHITECTURE.md — an explicit, separate action
     *  from registration. Caller (adapter-rest) is responsible for enforcing
     *  that only ADMIN can invoke this. */
    public User changeRole(UUID userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.changeRole(newRole);
        return userRepository.save(user);
    }
}
