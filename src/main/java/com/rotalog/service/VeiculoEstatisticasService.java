package com.rotalog.service;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.dto.EstatisticasFrotaResponse;
import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VeiculoEstatisticasService {

    private final VeiculoRepository veiculoRepository;

    public EstatisticasFrotaResponse obterEstatisticas() {
        return new EstatisticasFrotaResponse(
                veiculoRepository.count(),
                veiculoRepository.countByStatus(StatusVeiculo.ATIVO),
                veiculoRepository.countByStatus(StatusVeiculo.INATIVO),
                veiculoRepository.countByStatus(StatusVeiculo.MANUTENCAO));
    }
}
