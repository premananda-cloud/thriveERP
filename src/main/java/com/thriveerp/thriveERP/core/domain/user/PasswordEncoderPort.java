package com.thriveerp.thriveERP.core.domain.user;

/** Hashing is an infrastructure concern — kept behind a port so core-domain
 *  never imports Spring Security or any specific hashing library. */
public interface PasswordEncoderPort {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
