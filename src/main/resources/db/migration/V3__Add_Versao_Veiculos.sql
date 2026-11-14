-- Controle de concorrência otimista (@Version) em veiculos (ADR 0001, risco R2)
ALTER TABLE veiculos ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
