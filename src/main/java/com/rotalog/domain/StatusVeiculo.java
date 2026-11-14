package com.rotalog.domain;

import com.rotalog.exception.StatusVeiculoInvalidoException;

public enum StatusVeiculo {
    ATIVO,
    INATIVO,
    MANUTENCAO;

    /**
     * Transições permitidas: ATIVO -> INATIVO|MANUTENCAO, INATIVO -> ATIVO|MANUTENCAO, MANUTENCAO -> ATIVO.
     * Repetir o mesmo status é permitido (idempotente). Um veículo em manutenção não pode ser desativado
     * diretamente: a manutenção precisa ser concluída ou cancelada antes.
     */
    public boolean podeTransitarPara(StatusVeiculo destino) {
        // De ATIVO e INATIVO pode-se ir para qualquer outro status; de MANUTENCAO só para ATIVO.
        return this == destino || this != MANUTENCAO || destino == ATIVO;
    }

    /**
     * Converte texto no status correspondente. A comparação é estrita (maiúsculas, sem espaços extras).
     */
    public static StatusVeiculo from(String valor) {
        if (valor != null) {
            for (StatusVeiculo status : values()) {
                if (status.name().equals(valor)) {
                    return status;
                }
            }
        }
        throw new StatusVeiculoInvalidoException("Status inválido: " + valor);
    }
}
