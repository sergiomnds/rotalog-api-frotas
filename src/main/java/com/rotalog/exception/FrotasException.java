package com.rotalog.exception;

/**
 * Base das exceções de negócio do serviço de frotas.
 * Estende RuntimeException para manter compatibilidade com os controllers atuais.
 */
public abstract class FrotasException extends RuntimeException {

    protected FrotasException(String mensagem) {
        super(mensagem);
    }
}
