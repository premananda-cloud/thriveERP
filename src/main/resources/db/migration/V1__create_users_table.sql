-- Users table for register/login/role-grant.
-- Not in ARCHITECTURE.md's original data model (appointments/payments/audit_log) —
-- this is new scope; update ARCHITECTURE.md §5 once this settles.

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    version         INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- NOTE: there is deliberately no seed ADMIN row here. AuthService.register()
-- always creates CUSTOMER (see core/app/user/AuthService.java). To bootstrap
-- the first ADMIN, either:
--   a) register normally, then manually run:
--        UPDATE users SET role = 'ADMIN' WHERE username = '<you>';
--   b) or add a one-off Flyway seed migration once you have a real deploy
--      process — don't hardcode credentials into a migration file.
