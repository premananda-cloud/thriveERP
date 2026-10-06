package com.thriveerp.adapter.bootstrap;

import com.thriveerp.core.app.user.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN at startup from environment variables, so nobody has
 * to edit the database by hand. Does nothing unless all three variables are set,
 * and does nothing if an ADMIN already exists (see AuthService#ensureAdmin), so
 * it is safe to leave configured — but remove BOOTSTRAP_ADMIN_PASSWORD afterwards.
 *
 *   BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    static final int MIN_PASSWORD_LENGTH = 8; // same floor as RegisterRequest

    private final AuthService authService;
    private final String username;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(AuthService authService,
                                @Value("${app.bootstrap-admin.username:}") String username,
                                @Value("${app.bootstrap-admin.email:}") String email,
                                @Value("${app.bootstrap-admin.password:}") String password) {
        this.authService = authService;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean anySet = !username.isBlank() || !email.isBlank() || !password.isBlank();
        boolean allSet = !username.isBlank() && !email.isBlank() && !password.isBlank();

        if (!anySet) {
            return; // bootstrap not requested
        }
        if (!allSet) {
            throw new IllegalStateException(
                    "Incomplete admin bootstrap config: set all of BOOTSTRAP_ADMIN_USERNAME, " +
                    "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or none of them.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        authService.ensureAdmin(username, email, password).ifPresentOrElse(
                admin -> log.warn("Bootstrap admin '{}' is now an ADMIN. Remove BOOTSTRAP_ADMIN_PASSWORD " +
                        "from the environment.", admin.getUsername()),
                () -> log.info("Admin bootstrap skipped: an ADMIN already exists."));
    }
}
