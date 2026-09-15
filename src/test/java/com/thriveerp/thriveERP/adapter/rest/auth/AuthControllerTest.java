package com.thriveerp.thriveERP.adapter.rest.auth;

import tools.jackson.databind.ObjectMapper;
import com.thriveerp.thriveERP.adapter.rest.auth.dto.LoginRequest;
import com.thriveerp.thriveERP.adapter.rest.auth.dto.RegisterRequest;
import com.thriveerp.thriveERP.adapter.rest.common.GlobalExceptionHandler;
import com.thriveerp.thriveERP.adapter.security.TestJwtTokenServiceConfig;
import com.thriveerp.thriveERP.core.app.user.AuthService;
import com.thriveerp.thriveERP.core.domain.user.Role;
import com.thriveerp.thriveERP.core.domain.user.User;
import com.thriveerp.thriveERP.core.domain.user.exception.DuplicateUserException;
import com.thriveerp.thriveERP.core.domain.user.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test: HTTP mapping, validation, and exception->status translation
 * only. Security filters disabled here on purpose — /api/auth/** is public
 * per SecurityConfig, and re-testing the security chain in every controller
 * test would be redundant. UserAdminControllerTest is the one place that
 * exercises @PreAuthorize end-to-end.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, TestJwtTokenServiceConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;

    @Test
    void register_returns201_withUserBody_onSuccess() throws Exception {
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        when(authService.register("alice", "alice@example.com", "password123")).thenReturn(user);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("alice", "alice@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_returns409_whenUsernameOrEmailTaken() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateUserException("Username or email already registered"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("alice", "alice@example.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("alice", "alice@example.com", "short"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void register_returns400_whenEmailInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("alice", "not-an-email", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200_withBearerToken_onSuccess() throws Exception {
        when(authService.login("alice", "password123")).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_returns401_onBadCredentials() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice", "wrong"))))
                .andExpect(status().isUnauthorized());
    }
}
