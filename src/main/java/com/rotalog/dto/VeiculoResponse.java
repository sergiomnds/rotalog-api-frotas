package com.rotalog.dto;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Resposta da API de veículos. Mesmos campos que a entidade expunha, sem detalhes de persistência (ex.: versão).
 */
@Value
public class VeiculoResponse {

    Long id;
    String placa;
    String modelo;
    Integer anoFabricacao;
    Long quilometragem;
    StatusVeiculo status;
    LocalDateTime dataCadastro;
    LocalDateTime dataAtualizacao;

    public static VeiculoResponse from(Veiculo veiculo) {
        return new VeiculoResponse(
                veiculo.getId(),
                veiculo.getPlaca(),
                veiculo.getModelo(),
                veiculo.getAnoFabricacao(),
                veiculo.getQuilometragem(),
                veiculo.getStatus(),
                veiculo.getDataCadastro(),
                veiculo.getDataAtualizacao());
    }
}
