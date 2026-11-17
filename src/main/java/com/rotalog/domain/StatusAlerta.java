package com.rotalog.domain;

import com.rotalog.exception.StatusAlertaInvalidoException;

/**
 * Situação de um alerta de manutenção preventiva.
 * PENDENTE: ainda não foi entregue à api-notificacoes (ex.: serviço fora do ar), pode ser reprocessado.
 * ENVIADO: a api-notificacoes aceitou o pedido de envio.
 * FALHA: a api-notificacoes rejeitou o pedido (erro 4xx); reenviar sem corrigir não adianta.
 */
public enum StatusAlerta {
    PENDENTE,
    ENVIADO,
    FALHA;

    /** Converte texto no status correspondente (comparação estrita, em maiúsculas). */
    public static StatusAlerta from(String valor) {
        if (valor != null) {
            for (StatusAlerta status : values()) {
                if (status.name().equals(valor)) {
                    return status;
                }
            }
        }
        throw new StatusAlertaInvalidoException("Status de alerta inválido: " + valor);
    }
}
