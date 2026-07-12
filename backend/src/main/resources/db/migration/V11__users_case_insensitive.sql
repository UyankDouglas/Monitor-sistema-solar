-- =====================================================================
-- V11 · Unicidade case-insensitive de username e e-mail
-- Sem isto, 'admin' e 'Admin' seriam contas distintas (impersonation na
-- lista de usuários) e variantes de caixa do MESMO e-mail passariam pela
-- unicidade. Os seeds existentes já são lowercase — sem conflito.
-- =====================================================================

CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
