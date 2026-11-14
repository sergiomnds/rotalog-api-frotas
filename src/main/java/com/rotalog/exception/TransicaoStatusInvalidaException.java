package com.rotalog.exception;

import com.rotalog.domain.StatusVeiculo;

public class TransicaoStatusInvalidaException extends FrotasException {

    public TransicaoStatusInvalidaException(StatusVeiculo origem, StatusVeiculo destino) {
        super("Transição de status inválida: " + origem + " -> " + destino);
    }
}
