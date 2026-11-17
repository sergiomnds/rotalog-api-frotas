package com.rotalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificacaoClientTest {

    private static final String URL = "http://notificacoes.teste/api/notificacoes";

    private MockRestServiceServer server;
    private NotificacaoClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new NotificacaoClient(restTemplate, "http://notificacoes.teste");
    }

    @Test
    void enviaNotificacaoParaUrlConfigurada() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tipo").value("NOVO_VEICULO"))
                .andExpect(jsonPath("$.destinatario").value("gestor@rotalog.com"))
                .andExpect(jsonPath("$.mensagem").value("Olá"))
                .andExpect(jsonPath("$.canal").value("email"))
                .andRespond(withSuccess());

        client.enviarNotificacao("NOVO_VEICULO", "gestor@rotalog.com", "Olá");

        server.verify();
    }

    @Test
    void falhaDoServicoERepropagadaParaOChamador() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(HttpServerErrorException.class,
                () -> client.enviarNotificacao("TIPO", "a@b.com", "msg"));
    }

    @Test
    void enviaSms() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.canal").value("sms"))
                .andRespond(withSuccess());

        client.enviarSms("+5511999999999", "msg");

        server.verify();
    }

    @Test
    void falhaNoSmsEEngolida() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertDoesNotThrow(() -> client.enviarSms("+5511999999999", "msg"));
    }

    @Test
    void enviaAlertaDeManutencaoParaOEndpointDedicadoComOContratoEsperado() {
        server.expect(requestTo(URL + "/alertas-manutencao"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.alertaId").value(7))
                .andExpect(jsonPath("$.veiculoId").value(3))
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.motivo").value("TEMPO"))
                .andExpect(jsonPath("$.destinatario").value("gestor@rotalog.com"))
                .andExpect(jsonPath("$.mensagem").value("msg"))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.enviarAlertaManutencao(7L, 3L, "ABC1D23", "TEMPO", "gestor@rotalog.com", "msg");

        server.verify();
    }

    @Test
    void alertaNaoEnviadoPelasNotificacoesLancaExcecaoParaOChamadorRegistrarPendente() {
        server.expect(requestTo(URL + "/alertas-manutencao")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class,
                () -> client.enviarAlertaManutencao(7L, 3L, "ABC1D23", "TEMPO", "gestor@rotalog.com", "msg"));
    }

    @Test
    void alertaRejeitadoPorValidacaoLancaErroDeCliente() {
        server.expect(requestTo(URL + "/alertas-manutencao")).andRespond(withBadRequest());

        assertThrows(HttpClientErrorException.class,
                () -> client.enviarAlertaManutencao(7L, 3L, "ABC1D23", "TEMPO", "gestor@rotalog.com", "msg"));
    }
}
