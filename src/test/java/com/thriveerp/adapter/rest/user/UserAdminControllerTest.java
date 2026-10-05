package com.thriveerp.adapter.rest.user;

import tools.jackson.databind.ObjectMapper;
import com.thriveerp.adapter.rest.common.GlobalExceptionHandler;
import com.thriveerp.adapter.rest.user.dto.ChangeRoleRequest;
import com.thriveerp.adapter.security.SecurityConfig;
import com.thriveerp.adapter.security.TestJwtTokenServiceConfig;
import com.thriveerp.core.app.user.AuthService;
import com.thriveerp.core.domain.user.Role;
import com.thriveerp.core.domain.user.User;
import com.thriveerp.core.domain.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one controller test that runs the real security filter chain
 * (SecurityConfig + @EnableMethodSecurity), since role enforcement IS the
 * behavior under test here. Other controller tests disable security (see
 * AuthControllerTest) to stay focused on HTTP mapping.
 *
 * @WithMockUser injects the Authentication directly into the SecurityContext
 * before the request runs — it does not exercise JwtAuthenticationFilter's
 * parsing logic (that's JwtTokenServiceTest's job). TestJwtTokenServiceConfig
 * only exists to satisfy the (auto-scanned) filter's constructor dependency —
 * see that class's javadoc for why we don't also declare the filter bean here.
 */
@WebMvcTest(controllers = UserAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TestJwtTokenServiceConfig.class})
class UserAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_succeeds_forAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        User updated = new User(id, "alice", "alice@example.com", "hash",
                Role.STAFF, Instant.now(), Instant.now());
        when(authService.changeRole(id, Role.STAFF)).thenReturn(updated);

        mockMvc.perform(put("/api/users/{id}/role", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest(Role.STAFF))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changeRole_forbidden_forNonAdmin() throws Exception {
        mockMvc.perform(put("/api/users/{id}/role", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest(Role.STAFF))))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_unauthorized_withoutAuthentication() throws Exception {
        mockMvc.perform(put("/api/users/{id}/role", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest(Role.STAFF))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_returns404_whenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.changeRole(id, Role.STAFF))
                .thenThrow(new UserNotFoundException("User not found: " + id));

        mockMvc.perform(put("/api/users/{id}/role", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest(Role.STAFF))))
                .andExpect(status().isNotFound());
    }
}
