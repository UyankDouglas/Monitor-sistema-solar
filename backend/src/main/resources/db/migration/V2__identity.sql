-- =====================================================================
-- V2 · Identidade & Acesso
-- users, roles, user_roles, refresh_tokens + seed de roles e do
-- usuário administrador inicial.
-- =====================================================================

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    locale        VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    theme         VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT chk_users_theme   CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM'))
);

CREATE TABLE roles (
    id   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT uq_roles_name  UNIQUE (name),
    CONSTRAINT chk_roles_name CHECK (name IN ('ADMIN', 'USER'))
);

CREATE TABLE user_roles (
    user_id BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------
-- Seeds
-- Senha inicial do admin: "admin123" (BCrypt). Deve ser trocada no
-- primeiro acesso — fluxo implementado na etapa de segurança.
-- ---------------------------------------------------------------------
INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');

INSERT INTO users (username, email, password_hash, full_name)
VALUES ('admin',
        'admin@solarmonitor.local',
        '$2b$10$xrQ9RWVeNSA160IArAQIPOtQg5474gw.zZV8A74nTT.FQRch1TgFi',
        'Administrador');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         CROSS JOIN roles r
WHERE u.username = 'admin';
