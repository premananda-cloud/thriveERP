package com.thriveerp.thriveERP.core.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void newRegistration_defaultsToCustomerRole() {
        User user = User.newRegistration("alice", "alice@example.com", "hash");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void newRegistration_generatesNonNullId() {
        User user = User.newRegistration("alice", "alice@example.com", "hash");
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void newRegistration_setsCreatedAndUpdatedAtToSameInstant() {
        User user = User.newRegistration("alice", "alice@example.com", "hash");
        assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    void changeRole_updatesRole() {
        User user = User.newRegistration("alice", "alice@example.com", "hash");
        user.changeRole(Role.ADMIN);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void changeRole_advancesUpdatedAt_butNotCreatedAt() throws InterruptedException {
        User user = User.newRegistration("alice", "alice@example.com", "hash");
        var createdAt = user.getCreatedAt();
        Thread.sleep(5); // ensure a measurable gap between createdAt and the role-change timestamp

        user.changeRole(Role.STAFF);

        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isAfter(createdAt);
    }
}
