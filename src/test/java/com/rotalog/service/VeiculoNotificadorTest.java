package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VeiculoNotificadorTest {

    private static final String DESTINATARIO = "frota@exemplo.com";

    @Mock
    private NotificacaoClient notificacaoClient;

    private VeiculoNotificador notificador;
    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        notificador = new VeiculoNotificador(notificacaoClient, DESTINATARIO);
        veiculo = new Veiculo();
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Volvo FH");
        veiculo.setQuilometragem(50000L);
    }

    @Test
    void notificaNovoVeiculoParaDestinatarioConfigurado() {
        notificador.notificarNovoVeiculo(veiculo);

        verify(notificacaoClient).enviarNotificacao("NOVO_VEICULO", DESTINATARIO,
                "Novo veículo cadastrado: ABC1234 - Volvo FH");
    }

    @Test
    void notificaAlertaDeManutencao() {
        notificador.notificarAlertaManutencao(veiculo);

        verify(notificacaoClient).enviarNotificacao("ALERTA_MANUTENCAO", DESTINATARIO,
                "Veículo ABC1234 atingiu 50000 km. Agendar manutenção preventiva.");
    }

    @Test
    void notificaManutencaoAgendada() {
        notificador.notificarManutencaoAgendada(veiculo, 60000L);

        verify(notificacaoClient).enviarNotificacao("MANUTENCAO_AGENDADA", DESTINATARIO,
                "Manutenção preventiva agendada para veículo ABC1234 em 60000 km");
    }

    @Test
    void notificaDesativacao() {
        notificador.notificarDesativacao(veiculo);

        verify(notificacaoClient).enviarNotificacao("VEICULO_DESATIVADO", DESTINATARIO,
                "Veículo ABC1234 foi desativado");
    }

    @Test
    void falhaDeEnvioNaoInterrompeOFluxo() {
        doThrow(new IllegalStateException("fora do ar"))
                .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificador.notificarNovoVeiculo(veiculo));
    }
}
