package com.rotalog.dto;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import lombok.Value;

import java.time.LocalDateTime;

/** Resposta da API de alertas de manutenção preventiva. */
@Value
public class AlertaManutencaoResponse {

    Long id;
    Long veiculoId;
    String placa;
    MotivoAlerta motivo;
    String mensagem;
    StatusAlerta status;
    String detalheErro;
    int tentativas;
    LocalDateTime dataCriacao;
    LocalDateTime dataEnvio;

    public static AlertaManutencaoResponse from(AlertaManutencao alerta) {
        return new AlertaManutencaoResponse(
                alerta.getId(),
                alerta.getVeiculoId(),
                alerta.getPlaca(),
                alerta.getMotivo(),
                alerta.getMensagem(),
                alerta.getStatus(),
                alerta.getDetalheErro(),
                alerta.getTentativas(),
                alerta.getDataCriacao(),
                alerta.getDataEnvio());
    }
}
