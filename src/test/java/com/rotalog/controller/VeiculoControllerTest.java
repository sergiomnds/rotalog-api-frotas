package com.rotalog.controller;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.EstatisticasFrotaResponse;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.service.VeiculoEstatisticasService;
import com.rotalog.service.VeiculoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VeiculoControllerTest {

    @Mock
    private VeiculoService veiculoService;

    @Mock
    private VeiculoEstatisticasService estatisticasService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new VeiculoController(veiculoService, estatisticasService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private Veiculo veiculo() {
        Veiculo v = new Veiculo();
        v.setId(1L);
        v.setPlaca("ABC1234");
        v.setModelo("Volvo FH");
        v.setAnoFabricacao(2020);
        v.setQuilometragem(100L);
        v.setStatus(StatusVeiculo.ATIVO);
        v.setDataCadastro(LocalDateTime.of(2026, 1, 1, 10, 0));
        v.setVersao(7L);
        return v;
    }

    // ---- R7: paginação e DTO ----

    @Test
    void listarTodosDevolveArrayComHeadersDePaginacaoEPadraoDe100() throws Exception {
        when(veiculoService.listarTodos(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(veiculo()), PageRequest.of(0, 100), 250));

        mvc.perform(get("/veiculos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "250"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(jsonPath("$[0].placa").value("ABC1234"))
                .andExpect(jsonPath("$[0].status").value("ATIVO"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(veiculoService).listarTodos(captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void listarTodosRespeitaParametrosDePagina() throws Exception {
        when(veiculoService.listarTodos(any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));

        mvc.perform(get("/veiculos?page=2&size=10")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(veiculoService).listarTodos(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void respostaNaoExpoeAVersaoDaEntidade() throws Exception {
        when(veiculoService.buscarPorId(1L)).thenReturn(veiculo());

        mvc.perform(get("/veiculos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.anoFabricacao").value(2020))
                .andExpect(jsonPath("$.quilometragem").value(100))
                .andExpect(jsonPath("$.versao").doesNotExist());
    }

    @Test
    void listarPorStatusPagina() throws Exception {
        when(veiculoService.obterVeiculosPorStatus(eq("ATIVO"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(veiculo())));

        mvc.perform(get("/veiculos/status/ATIVO"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].placa").value("ABC1234"));
    }

    // ---- Endpoints felizes ----

    @Test
    void buscarPorPlaca() throws Exception {
        when(veiculoService.buscarPorPlaca("ABC1234")).thenReturn(veiculo());

        mvc.perform(get("/veiculos/placa/ABC1234")).andExpect(jsonPath("$.modelo").value("Volvo FH"));
    }

    @Test
    void registrarDevolve201() throws Exception {
        when(veiculoService.registrarVeiculo("ABC1234", "Volvo FH", 2020)).thenReturn(veiculo());

        mvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC1234\",\"modelo\":\"Volvo FH\",\"anoFabricacao\":2020}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1234"));
    }

    @Test
    void atualizar() throws Exception {
        when(veiculoService.atualizarVeiculo(1L, "Novo", 2021, 5L)).thenReturn(veiculo());

        mvc.perform(put("/veiculos/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelo\":\"Novo\",\"anoFabricacao\":2021,\"quilometragem\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarQuilometragem() throws Exception {
        when(veiculoService.atualizarQuilometragem(1L, 500L)).thenReturn(veiculo());

        mvc.perform(patch("/veiculos/1/quilometragem").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quilometragem\":500}"))
                .andExpect(status().isOk());
    }

    @Test
    void desativarEReativar() throws Exception {
        when(veiculoService.desativarVeiculo(1L)).thenReturn(veiculo());
        when(veiculoService.reativarVeiculo(1L)).thenReturn(veiculo());

        mvc.perform(patch("/veiculos/1/desativar")).andExpect(status().isOk());
        mvc.perform(patch("/veiculos/1/reativar")).andExpect(status().isOk());
    }

    @Test
    void estatisticasEmJsonComCamposOriginais() throws Exception {
        when(estatisticasService.obterEstatisticas()).thenReturn(new EstatisticasFrotaResponse(6, 3, 2, 1));

        mvc.perform(get("/veiculos/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.ativos").value(3))
                .andExpect(jsonPath("$.inativos").value(2))
                .andExpect(jsonPath("$.em_manutencao").value(1));
    }

    // ---- R1, R2, R5, R6: tradução de erros ----

    @Test
    void naoEncontradoVira404() throws Exception {
        when(veiculoService.buscarPorId(99L)).thenThrow(VeiculoNaoEncontradoException.porId(99L));

        mvc.perform(get("/veiculos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Veículo não encontrado: 99"));
    }

    @Test
    void erroDeValidacaoVira400() throws Exception {
        when(veiculoService.obterVeiculosPorStatus(eq("X"), any(Pageable.class)))
                .thenThrow(new com.rotalog.exception.StatusVeiculoInvalidoException("Status inválido: X"));

        mvc.perform(get("/veiculos/status/X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status inválido: X"));
    }

    @Test
    void placaDuplicadaVira409() throws Exception {
        when(veiculoService.registrarVeiculo(anyString(), anyString(), any()))
                .thenThrow(new PlacaDuplicadaException("ABC1234"));

        mvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC1234\",\"modelo\":\"M\",\"anoFabricacao\":2020}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Veículo com placa ABC1234 já existe"));
    }

    @Test
    void transicaoInvalidaVira409() throws Exception {
        when(veiculoService.desativarVeiculo(1L))
                .thenThrow(new TransicaoStatusInvalidaException(StatusVeiculo.MANUTENCAO, StatusVeiculo.INATIVO));

        mvc.perform(patch("/veiculos/1/desativar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Transição de status inválida: MANUTENCAO -> INATIVO"));
    }

    @Test
    void violacaoDeIntegridadeSemTratamentoNoServicoVira409SemVazarDetalhes() throws Exception {
        when(veiculoService.atualizarVeiculo(anyLong(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("ERROR: duplicate key constraint uk_placa"));

        mvc.perform(put("/veiculos/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito com dados já existentes"));
    }

    @Test
    void conflitoDeConcorrenciaVira409() throws Exception {
        when(veiculoService.desativarVeiculo(1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Veiculo.class, 1L));

        mvc.perform(patch("/veiculos/1/desativar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("O registro foi alterado por outra requisição. Tente novamente."));
    }

    @Test
    void erroDeInfraestruturaVira500GenericoSemVazarMensagemInterna() throws Exception {
        when(veiculoService.buscarPorId(1L))
                .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://db:5432/rotalog senha=x"));

        mvc.perform(get("/veiculos/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Erro interno do servidor"));
    }

    @Test
    void jsonInvalidoContinuaSendo400NoMesmoFormato() throws Exception {
        mvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Bad Request"));
    }

    @Test
    void metodoNaoSuportadoContinuaSendo405() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/veiculos/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.erro").value("Method Not Allowed"));
    }
}
