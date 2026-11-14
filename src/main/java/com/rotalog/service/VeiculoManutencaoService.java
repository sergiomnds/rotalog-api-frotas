package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.QuilometragemInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Regras de manutenção de veículos: necessidade, custo estimado e agendamento preventivo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoManutencaoService {

    private static final long LIMITE_QUILOMETRAGEM = 50000L;
    private static final BigDecimal CUSTO_BASE = new BigDecimal("500.00");
    private static final BigDecimal CUSTO_POR_KM = new BigDecimal("0.05");

    private final VeiculoNotificador notificador;

    public boolean precisaDeManutencao(Veiculo veiculo) {
        return atingeLimite(veiculo.getQuilometragem());
    }

    /**
     * Verdadeiro apenas na transição: estava abaixo do limite (ou sem leitura) e passou a atingi-lo.
     * Evita repetir o alerta a cada atualização de um veículo que já estava acima do limite.
     */
    public boolean cruzouLimite(Long quilometragemAnterior, Long quilometragemAtual) {
        return atingeLimite(quilometragemAtual) && !atingeLimite(quilometragemAnterior);
    }

    private boolean atingeLimite(Long quilometragem) {
        return quilometragem != null && quilometragem >= LIMITE_QUILOMETRAGEM;
    }

    public BigDecimal calcularCustoManutencao(Long quilometragem) {
        if (quilometragem == null || quilometragem < 0) {
            throw new QuilometragemInvalidaException("Quilometragem inválida para cálculo de custo");
        }
        return CUSTO_BASE.add(CUSTO_POR_KM.multiply(BigDecimal.valueOf(quilometragem)));
    }

    public void agendarManutencaoPreventiva(Veiculo veiculo, Long quilometragemLimite) {
        log.info("Manutenção preventiva agendada para veículo {} em {} km", veiculo.getPlaca(), quilometragemLimite);
        notificador.notificarManutencaoAgendada(veiculo, quilometragemLimite);
    }
}
