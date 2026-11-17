package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManutencaoPreventivaServiceTest {

    private static final String DESTINATARIO = "gestor@exemplo.com";
    private static final String CONCLUIDA = "CONCLUIDA";

    @Mock
    private VeiculoRepository veiculoRepository;
    @Mock
    private ManutencaoRepository manutencaoRepository;
    @Mock
    private AlertaManutencaoRepository alertaRepository;
    @Mock
    private NotificacaoClient notificacaoClient;

    private ManutencaoPreventivaService service;

    @BeforeEach
    void setUp() {
        service = new ManutencaoPreventivaService(veiculoRepository, manutencaoRepository, alertaRepository,
                notificacaoClient, 10000L, 6, DESTINATARIO);
    }

    @Test
    void alertaPorQuilometragemQuandoRodouMaisQueOLimiteDesdeAUltimaManutencao() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        salvaRetornandoOArgumento();

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(1, alertas.size());
        assertEquals(MotivoAlerta.QUILOMETRAGEM, alertas.get(0).getMotivo());
        assertEquals(StatusAlerta.ENVIADO, alertas.get(0).getStatus());
        verify(notificacaoClient).enviarAlertaManutencao(any(), any(), eq("ABC1D23"), eq("QUILOMETRAGEM"),
                eq(DESTINATARIO), anyString());
    }

    @Test
    void alertaPorTempoQuandoPassouDoLimiteDeMeses() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 20000L, meses(24));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 18000L, LocalDateTime.now().minusMonths(8));
        salvaRetornandoOArgumento();

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(1, alertas.size());
        assertEquals(MotivoAlerta.TEMPO, alertas.get(0).getMotivo());
    }

    @Test
    void alertaPorQuilometragemETempoQuandoAmbosOsCriteriosSaoAtingidos() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 140000L, meses(30));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 120000L, LocalDateTime.now().minusMonths(9));
        salvaRetornandoOArgumento();

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(MotivoAlerta.QUILOMETRAGEM_E_TEMPO, alertas.get(0).getMotivo());
    }

    @Test
    void semManutencaoAnteriorUsaADataDeCadastroEKmZeroComoReferencia() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 5000L, meses(10));
        veiculoAtivo(veiculo);
        when(manutencaoRepository.findFirstByVeiculoIdAndStatusAndDataManutencaoIsNotNullOrderByDataManutencaoDesc(
                1L, CONCLUIDA)).thenReturn(Optional.empty());
        salvaRetornandoOArgumento();

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(1, alertas.size());
        assertEquals(MotivoAlerta.TEMPO, alertas.get(0).getMotivo());
    }

    @Test
    void naoGeraAlertaQuandoNenhumCriterioEAtingido() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 32000L, meses(12));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 30000L, LocalDateTime.now().minusMonths(1));

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertTrue(alertas.isEmpty());
        verify(notificacaoClient, never()).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void naoGeraAlertaDuplicadoQuandoJaExisteAlertaDesdeAUltimaManutencao() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        when(alertaRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(eq(1L), anyCollection(), any()))
                .thenReturn(true);

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertTrue(alertas.isEmpty());
        verify(notificacaoClient, never()).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notificacoesForaDoArRegistraAlertaComoPendente() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        salvaRetornandoOArgumento();
        doThrow(new ResourceAccessException("Connection refused", new IOException("refused")))
                .when(notificacaoClient).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        AlertaManutencao alerta = alertas.get(0);
        assertEquals(StatusAlerta.PENDENTE, alerta.getStatus());
        assertEquals(1, alerta.getTentativas());
        assertNull(alerta.getDataEnvio());
        assertNotNull(alerta.getDetalheErro());
    }

    @Test
    void erroDeServidorNasNotificacoesTambemFicaPendente() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        salvaRetornandoOArgumento();
        doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(notificacaoClient).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(StatusAlerta.PENDENTE, alertas.get(0).getStatus());
    }

    @Test
    void pedidoRejeitadoPelasNotificacoesViraFalha() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        salvaRetornandoOArgumento();
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(notificacaoClient).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());

        List<AlertaManutencao> alertas = service.verificarEAlertar();

        assertEquals(StatusAlerta.FALHA, alertas.get(0).getStatus());
    }

    @Test
    void alertaEPersistidoComoPendenteAntesDeChamarAsNotificacoes() {
        Veiculo veiculo = veiculo(1L, "ABC1D23", 58000L, meses(20));
        veiculoAtivo(veiculo);
        ultimaManutencao(1L, 45000L, LocalDateTime.now().minusMonths(1));
        salvaRetornandoOArgumento();

        service.verificarEAlertar();

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(alertaRepository, notificacaoClient);
        ordem.verify(alertaRepository).save(any(AlertaManutencao.class));
        ordem.verify(notificacaoClient).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());
        ordem.verify(alertaRepository).save(any(AlertaManutencao.class));
    }

    @Test
    void reprocessarEnviaOsPendentesEAtualizaOStatus() {
        AlertaManutencao pendente = new AlertaManutencao();
        pendente.setId(9L);
        pendente.setVeiculoId(3L);
        pendente.setPlaca("ABC1D23");
        pendente.setMotivo(MotivoAlerta.TEMPO);
        pendente.setMensagem("mensagem");
        pendente.setStatus(StatusAlerta.PENDENTE);
        pendente.setTentativas(1);
        when(alertaRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE))
                .thenReturn(Collections.singletonList(pendente));

        List<AlertaManutencao> reprocessados = service.reprocessarPendentes();

        assertEquals(1, reprocessados.size());
        assertEquals(StatusAlerta.ENVIADO, pendente.getStatus());
        verify(notificacaoClient).enviarAlertaManutencao(9L, 3L, "ABC1D23", "TEMPO", DESTINATARIO, "mensagem");
        assertEquals(2, pendente.getTentativas());
        assertNotNull(pendente.getDataEnvio());
        verify(alertaRepository).save(pendente);
    }

    @Test
    void reprocessarMantemPendenteSeAsNotificacoesContinuamForaDoAr() {
        AlertaManutencao pendente = new AlertaManutencao();
        pendente.setId(9L);
        pendente.setVeiculoId(3L);
        pendente.setPlaca("ABC1D23");
        pendente.setMotivo(MotivoAlerta.TEMPO);
        pendente.setMensagem("mensagem");
        pendente.setStatus(StatusAlerta.PENDENTE);
        when(alertaRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE))
                .thenReturn(Collections.singletonList(pendente));
        doThrow(new ResourceAccessException("timeout"))
                .when(notificacaoClient).enviarAlertaManutencao(any(), any(), anyString(), anyString(), anyString(), anyString());

        service.reprocessarPendentes();

        assertEquals(StatusAlerta.PENDENTE, pendente.getStatus());
        assertEquals(1, pendente.getTentativas());
    }

    @Test
    void listarSemFiltroRetornaTodosEComFiltroApenasOStatusInformado() {
        service.listar(null);
        service.listar(StatusAlerta.PENDENTE);

        verify(alertaRepository).findAllByOrderByDataCriacaoDesc();
        verify(alertaRepository).findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE);
    }

    // --- helpers ---

    private Veiculo veiculo(Long id, String placa, Long km, LocalDateTime dataCadastro) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Fiat Fiorino");
        veiculo.setQuilometragem(km);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        veiculo.setDataCadastro(dataCadastro);
        return veiculo;
    }

    private void veiculoAtivo(Veiculo veiculo) {
        when(veiculoRepository.findAllByStatus(StatusVeiculo.ATIVO))
                .thenReturn(Collections.singletonList(veiculo));
    }

    private void ultimaManutencao(Long veiculoId, Long km, LocalDateTime data) {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculoId);
        manutencao.setQuilometragemManutencao(km);
        manutencao.setDataManutencao(data);
        manutencao.setStatus(CONCLUIDA);
        when(manutencaoRepository.findFirstByVeiculoIdAndStatusAndDataManutencaoIsNotNullOrderByDataManutencaoDesc(
                veiculoId, CONCLUIDA)).thenReturn(Optional.of(manutencao));
    }

    private void salvaRetornandoOArgumento() {
        when(alertaRepository.save(any(AlertaManutencao.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LocalDateTime meses(int atras) {
        return LocalDateTime.now().minusMonths(atras);
    }
}
