package com.thriveerp.thriveERP.core.domain.user;

/** Token issuance is an infrastructure concern (JWT library, secret, etc.) —
 *  kept behind a port for the same reason as PasswordEncoderPort. */
public interface TokenServicePort {
    String issueToken(User user);
}
