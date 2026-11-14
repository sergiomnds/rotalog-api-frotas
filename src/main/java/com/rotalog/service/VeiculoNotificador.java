package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Monta e envia as notificações de veículo ao gestor da frota.
 * Falhas de envio são registradas em log e nunca interrompem o fluxo de negócio.
 */
@Slf4j
@Component
public class VeiculoNotificador {

    private final NotificacaoClient notificacaoClient;
    private final String destinatario;

    public VeiculoNotificador(NotificacaoClient notificacaoClient,
                              @Value("${rotalog.notificacao.gestor:gestor@rotalog.com}") String destinatario) {
        this.notificacaoClient = notificacaoClient;
        this.destinatario = destinatario;
    }

    public void notificarNovoVeiculo(Veiculo veiculo) {
        enviar("NOVO_VEICULO",
                "Novo veículo cadastrado: " + veiculo.getPlaca() + " - " + veiculo.getModelo());
    }

    public void notificarAlertaManutencao(Veiculo veiculo) {
        enviar("ALERTA_MANUTENCAO",
                "Veículo " + veiculo.getPlaca() + " atingiu " + veiculo.getQuilometragem()
                        + " km. Agendar manutenção preventiva.");
    }

    public void notificarManutencaoAgendada(Veiculo veiculo, Long quilometragemLimite) {
        enviar("MANUTENCAO_AGENDADA",
                "Manutenção preventiva agendada para veículo " + veiculo.getPlaca()
                        + " em " + quilometragemLimite + " km");
    }

    public void notificarDesativacao(Veiculo veiculo) {
        enviar("VEICULO_DESATIVADO", "Veículo " + veiculo.getPlaca() + " foi desativado");
    }

    private void enviar(String tipo, String mensagem) {
        try {
            notificacaoClient.enviarNotificacao(tipo, destinatario, mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação {}: {}", tipo, e.getMessage());
        }
    }
}
