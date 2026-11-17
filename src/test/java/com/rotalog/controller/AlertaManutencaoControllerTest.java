package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.service.ManutencaoPreventivaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlertaManutencaoControllerTest {

    @Mock
    private ManutencaoPreventivaService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AlertaManutencaoController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listaAlertasFiltrandoPorStatus() throws Exception {
        when(service.listar(StatusAlerta.PENDENTE)).thenReturn(Collections.singletonList(alerta()));

        mvc.perform(get("/alertas-manutencao").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].motivo").value("QUILOMETRAGEM"));
    }

    @Test
    void listaTodosOsAlertasSemFiltro() throws Exception {
        when(service.listar(null)).thenReturn(Collections.singletonList(alerta()));

        mvc.perform(get("/alertas-manutencao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void statusInvalidoRetorna400() throws Exception {
        mvc.perform(get("/alertas-manutencao").param("status", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status de alerta inválido: XPTO"));
    }

    @Test
    void verificacaoRetornaOsAlertasGerados() throws Exception {
        when(service.verificarEAlertar()).thenReturn(Collections.singletonList(alerta()));

        mvc.perform(post("/alertas-manutencao/verificacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"));
    }

    @Test
    void reprocessamentoDelegaParaOServico() throws Exception {
        when(service.reprocessarPendentes()).thenReturn(Collections.emptyList());

        mvc.perform(post("/alertas-manutencao/reprocessamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).reprocessarPendentes();
    }

    private AlertaManutencao alerta() {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setId(1L);
        alerta.setVeiculoId(10L);
        alerta.setPlaca("ABC1D23");
        alerta.setMotivo(MotivoAlerta.QUILOMETRAGEM);
        alerta.setMensagem("mensagem");
        alerta.setStatus(StatusAlerta.PENDENTE);
        alerta.setDataCriacao(LocalDateTime.now());
        return alerta;
    }
}
