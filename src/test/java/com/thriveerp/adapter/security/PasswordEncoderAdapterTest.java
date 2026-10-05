package com.thriveerp.adapter.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderAdapterTest {

    private final PasswordEncoderAdapter encoder = new PasswordEncoderAdapter();

    @Test
    void hash_doesNotReturnRawPassword() {
        String hash = encoder.hash("password123");
        assertThat(hash).isNotEqualTo("password123");
    }

    @Test
    void hash_isSalted_producesDifferentHashesForTheSameInput() {
        String hash1 = encoder.hash("password123");
        String hash2 = encoder.hash("password123");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void matches_returnsTrue_forTheCorrectPassword() {
        String hash = encoder.hash("password123");
        assertThat(encoder.matches("password123", hash)).isTrue();
    }

    @Test
    void matches_returnsFalse_forTheWrongPassword() {
        String hash = encoder.hash("password123");
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }
}
