package com.thriveerp.thriveERP.core.domain.user;

/**
 * Pure Java. No framework annotations here — see groundrule.txt §2.
 * KISS per DesignLogic.md: three roles is enough for now. Expand only when
 * a real privilege requirement shows up.
 */
public enum Role {
    CUSTOMER,
    STAFF,
    ADMIN
}
