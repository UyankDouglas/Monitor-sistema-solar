-- =====================================================================
-- V10 · Segurança (Etapa 6)
-- Flag de troca de senha obrigatória. O admin seedado na V2 usa a senha
-- inicial de fábrica (admin123) — o primeiro login exige troca.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET must_change_password = TRUE
WHERE username = 'admin';
