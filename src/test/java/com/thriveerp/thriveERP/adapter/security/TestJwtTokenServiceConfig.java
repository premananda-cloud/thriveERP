package com.thriveerp.thriveERP.adapter.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @WebMvcTest slices auto-include Filter-type beans in their component scan
 * (that's Spring Boot's default behavior, not something we opted into) — so
 * the real JwtAuthenticationFilter gets pulled into every @WebMvcTest
 * context automatically, even when its controller has nothing to do with
 * security. That filter's constructor needs a JwtTokenService, which is
 * NOT auto-included (plain @Component, not a Filter/Controller/etc.), so we
 * provide one here with a fixed test-only secret.
 *
 * Deliberately does NOT also declare a JwtAuthenticationFilter @Bean — doing
 * so collides with the auto-scanned one (same bean name, BeanDefinitionOverride
 * exception). Let the real filter be constructed; just feed it what it needs.
 */
@TestConfiguration
public class TestJwtTokenServiceConfig {

    @Bean
    JwtTokenService jwtTokenService() {
        return new JwtTokenService("test-only-secret-value-32-bytes-minimum!", 3600);
    }
}
