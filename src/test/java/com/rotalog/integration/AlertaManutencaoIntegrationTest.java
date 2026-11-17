package com.rotalog.integration;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.ManutencaoPreventivaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integração do alerta de manutenção preventiva: service + repositórios + mapeamento JPA contra o PostgreSQL
 * real do docker-compose (schema criado pelos scripts 10/11 do rotalog-workspace), com a api-notificacoes
 * simulada por MockRestServiceServer. Cada teste roda em transação e é revertido ao final.
 * <p>
 * É ignorado quando o PostgreSQL não está acessível, para não quebrar o {@code mvn test} sem o docker no ar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("bancoDisponivel")
class AlertaManutencaoIntegrationTest {

    private static final String URL_NOTIFICACOES = "http://localhost:5000/api/notificacoes/alertas-manutencao";
    private static final String PLACA_KM = "IT1K234";
    private static final String PLACA_TEMPO = "IT2T345";
    private static final String PLACA_OK = "IT3O456";
    private static final String PLACA_INATIVO = "IT4I567";

    @Autowired
    private ManutencaoPreventivaService service;
    @Autowired
    private VeiculoRepository veiculoRepository;
    @Autowired
    private ManutencaoRepository manutencaoRepository;
    @Autowired
    private AlertaManutencaoRepository alertaRepository;
    @Autowired
    @Qualifier("notificacaoRestTemplate")
    private RestTemplate notificacaoRestTemplate;
    @Autowired
    private MockMvc mvc;

    private MockRestServiceServer notificacoes;

    static boolean bancoDisponivel() {
        try (Connection ignored = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/rotalog?currentSchema=frotas", "rotalog_admin", "rotalog123")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        notificacoes = MockRestServiceServer.bindTo(notificacaoRestTemplate).ignoreExpectOrder(true).build();

        // 13.000 km desde a última manutenção (há 1 mês) -> elegível por quilometragem
        Veiculo porKm = veiculo(PLACA_KM, 58000L, StatusVeiculo.ATIVO, LocalDateTime.now().minusMonths(18));
        manutencaoConcluida(porKm, 45000L, LocalDateTime.now().minusMonths(1));
        // 8 meses desde a última manutenção, poucos km -> elegível por tempo
        Veiculo porTempo = veiculo(PLACA_TEMPO, 20000L, StatusVeiculo.ATIVO, LocalDateTime.now().minusMonths(24));
        manutencaoConcluida(porTempo, 18000L, LocalDateTime.now().minusMonths(8));
        // manutenção recente e poucos km desde então -> não elegível
        Veiculo ok = veiculo(PLACA_OK, 32000L, StatusVeiculo.ATIVO, LocalDateTime.now().minusMonths(12));
        manutencaoConcluida(ok, 30000L, LocalDateTime.now().minusMonths(1));
        // muito rodado, porém inativo -> não elegível
        veiculo(PLACA_INATIVO, 250000L, StatusVeiculo.INATIVO, LocalDateTime.now().minusMonths(40));
    }

    @Test
    void gravaAlertasDosElegiveisComoEnviadoQuandoANotificacaoFunciona() {
        notificacoesResponde(withSuccess());

        service.verificarEAlertar();

        AlertaManutencao porKm = unicoAlertaDe(PLACA_KM);
        assertEquals(MotivoAlerta.QUILOMETRAGEM, porKm.getMotivo());
        assertEquals(StatusAlerta.ENVIADO, porKm.getStatus());
        assertEquals(1, porKm.getTentativas());
        assertNotNull(porKm.getId());
        assertNotNull(porKm.getDataEnvio());
        assertNull(porKm.getDetalheErro());

        assertEquals(MotivoAlerta.TEMPO, unicoAlertaDe(PLACA_TEMPO).getMotivo());
    }

    @Test
    void naoGeraAlertaParaVeiculoRecenteNemParaVeiculoInativo() {
        notificacoesResponde(withSuccess());

        service.verificarEAlertar();

        assertTrue(alertasDe(PLACA_OK).isEmpty());
        assertTrue(alertasDe(PLACA_INATIVO).isEmpty());
    }

    @Test
    void notificacoesForaDoArDeixaOAlertaPendenteERegistradoNoBanco() {
        notificacoesResponde(withServerError());

        service.verificarEAlertar();

        AlertaManutencao alerta = unicoAlertaDe(PLACA_KM);
        assertEquals(StatusAlerta.PENDENTE, alerta.getStatus());
        assertEquals(1, alerta.getTentativas());
        assertNull(alerta.getDataEnvio());
        assertNotNull(alerta.getDetalheErro());
    }

    @Test
    void pedidoRejeitadoPelasNotificacoesFicaComoFalha() {
        notificacoesResponde(withBadRequest());

        service.verificarEAlertar();

        assertEquals(StatusAlerta.FALHA, unicoAlertaDe(PLACA_KM).getStatus());
    }

    @Test
    void reprocessamentoEnviaOsPendentesQuandoAsNotificacoesVoltam() {
        notificacoesResponde(withServerError());
        service.verificarEAlertar();
        assertEquals(StatusAlerta.PENDENTE, unicoAlertaDe(PLACA_KM).getStatus());

        notificacoes.reset();
        notificacoesResponde(withSuccess());
        service.reprocessarPendentes();

        AlertaManutencao alerta = unicoAlertaDe(PLACA_KM);
        assertEquals(StatusAlerta.ENVIADO, alerta.getStatus());
        assertEquals(2, alerta.getTentativas());
        assertNotNull(alerta.getDataEnvio());
        assertNull(alerta.getDetalheErro());
    }

    @Test
    void segundaVerificacaoNaoDuplicaAlertaPendenteNemEnviado() {
        notificacoesResponde(withSuccess());
        service.verificarEAlertar();
        service.verificarEAlertar();

        assertEquals(1, alertasDe(PLACA_KM).size());
        assertEquals(1, alertasDe(PLACA_TEMPO).size());
    }

    @Test
    void novaManutencaoConcluidaPermiteNovoAlertaNoCicloSeguinte() {
        notificacoesResponde(withSuccess());
        service.verificarEAlertar();
        assertEquals(1, alertasDe(PLACA_KM).size());

        // veículo faz manutenção e, depois, volta a ultrapassar o limite de km
        Veiculo veiculo = veiculoRepository.findByPlaca(PLACA_KM).get();
        manutencaoConcluida(veiculo, 58000L, LocalDateTime.now().plusMinutes(1));
        veiculo.setQuilometragem(70000L);
        veiculoRepository.save(veiculo);
        service.verificarEAlertar();

        assertEquals(2, alertasDe(PLACA_KM).size());
    }

    @Test
    void listarFiltraPorStatus() {
        notificacoesResponde(withServerError());
        service.verificarEAlertar();

        List<String> pendentes = service.listar(StatusAlerta.PENDENTE).stream()
                .map(AlertaManutencao::getPlaca).collect(Collectors.toList());
        List<AlertaManutencao> enviados = service.listar(StatusAlerta.ENVIADO);

        assertTrue(pendentes.contains(PLACA_KM));
        assertTrue(pendentes.contains(PLACA_TEMPO));
        assertTrue(enviados.stream().noneMatch(a -> PLACA_KM.equals(a.getPlaca())));
        assertTrue(service.listar(null).size() >= pendentes.size());
    }

    @Test
    void apiListaAlertasFiltradosPorStatusEmJson() throws Exception {
        notificacoesResponde(withServerError());
        mvc.perform(post("/alertas-manutencao/verificacao")).andExpect(status().isOk());

        mvc.perform(get("/alertas-manutencao").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.placa == '" + PLACA_KM + "')].status").value("PENDENTE"))
                .andExpect(jsonPath("$[?(@.placa == '" + PLACA_KM + "')].motivo").value("QUILOMETRAGEM"));
    }

    @Test
    void apiRejeitaStatusInvalidoComBadRequest() throws Exception {
        mvc.perform(get("/alertas-manutencao").param("status", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status de alerta inválido: XPTO"));
    }

    // --- helpers ---

    private void notificacoesResponde(org.springframework.test.web.client.ResponseCreator resposta) {
        notificacoes.expect(ExpectedCount.manyTimes(), requestTo(URL_NOTIFICACOES))
                .andExpect(method(HttpMethod.POST))
                .andRespond(resposta);
    }

    private Veiculo veiculo(String placa, Long km, StatusVeiculo status, LocalDateTime cadastro) {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa);
        veiculo.setModelo("Veículo de teste");
        veiculo.setAnoFabricacao(2020);
        veiculo.setQuilometragem(km);
        veiculo.setStatus(status);
        veiculo.setDataCadastro(cadastro);
        veiculo.setDataAtualizacao(cadastro);
        return veiculoRepository.save(veiculo);
    }

    private void manutencaoConcluida(Veiculo veiculo, Long km, LocalDateTime data) {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculo.getId());
        manutencao.setTipoManutencao("PREVENTIVA");
        manutencao.setStatus("CONCLUIDA");
        manutencao.setQuilometragemManutencao(km);
        manutencao.setDataManutencao(data);
        manutencao.setDataCriacao(data);
        manutencao.setDataAtualizacao(data);
        manutencaoRepository.save(manutencao);
    }

    private List<AlertaManutencao> alertasDe(String placa) {
        alertaRepository.flush();
        return alertaRepository.findAllByOrderByDataCriacaoDesc().stream()
                .filter(a -> placa.equals(a.getPlaca()))
                .collect(Collectors.toList());
    }

    private AlertaManutencao unicoAlertaDe(String placa) {
        List<AlertaManutencao> alertas = alertasDe(placa);
        assertEquals(1, alertas.size(), "alertas de " + placa);
        Optional<AlertaManutencao> alerta = alertas.stream().findFirst();
        return alerta.get();
    }
}
