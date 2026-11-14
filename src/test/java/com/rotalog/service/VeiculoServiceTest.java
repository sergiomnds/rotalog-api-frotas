package com.rotalog.service;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.exception.AnoFabricacaoInvalidoException;
import com.rotalog.exception.ModeloInvalidoException;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import com.rotalog.exception.StatusVeiculoInvalidoException;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private VeiculoNotificador notificador;

    @Mock
    private VeiculoManutencaoService manutencaoService;

    private VeiculoService veiculoService;

    @BeforeEach
    void setUp() {
        veiculoService = new VeiculoService(veiculoRepository, new VeiculoValidator(), notificador, manutencaoService);
    }

    private Veiculo criarVeiculo(Long id, String placa, Long quilometragem) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Volvo FH");
        veiculo.setAnoFabricacao(2020);
        veiculo.setQuilometragem(quilometragem);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        return veiculo;
    }

    private void veiculoExiste(Veiculo veiculo) {
        when(veiculoRepository.findById(veiculo.getId())).thenReturn(Optional.of(veiculo));
    }

    private void saveRetornaArgumento() {
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Consultas")
    class Consultas {

        @Test
        void listarTodos() {
            List<Veiculo> veiculos = Arrays.asList(criarVeiculo(1L, "ABC1234", 0L), criarVeiculo(2L, "DEF5678", 10L));
            Pageable pageable = PageRequest.of(0, 10);
            Page<Veiculo> pagina = new PageImpl<>(veiculos);
            when(veiculoRepository.findAll(pageable)).thenReturn(pagina);

            assertSame(pagina, veiculoService.listarTodos(pageable));
        }

        @Test
        void buscarPorIdEncontrado() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculoExiste(veiculo);

            assertSame(veiculo, veiculoService.buscarPorId(1L));
        }

        @Test
        void buscarPorIdNaoEncontrado() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            VeiculoNaoEncontradoException ex = assertThrows(VeiculoNaoEncontradoException.class,
                    () -> veiculoService.buscarPorId(99L));

            assertEquals("Veículo não encontrado: 99", ex.getMessage());
        }

        @Test
        @DisplayName("buscarPorPlaca padroniza a placa (trim + maiúsculas)")
        void buscarPorPlacaEncontrado() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));

            assertSame(veiculo, veiculoService.buscarPorPlaca("  abc1234 "));
        }

        @Test
        void buscarPorPlacaNaoEncontrado() {
            when(veiculoRepository.findByPlaca("XXX0000")).thenReturn(Optional.empty());

            VeiculoNaoEncontradoException ex = assertThrows(VeiculoNaoEncontradoException.class,
                    () -> veiculoService.buscarPorPlaca("xxx0000"));

            assertEquals("Veículo não encontrado com placa: XXX0000", ex.getMessage());
        }

        @Test
        @DisplayName("buscarPorPlaca com placa nula resulta em não encontrado, sem NPE")
        void buscarPorPlacaNula() {
            when(veiculoRepository.findByPlaca(null)).thenReturn(Optional.empty());

            assertThrows(VeiculoNaoEncontradoException.class, () -> veiculoService.buscarPorPlaca(null));
        }

        @Test
        void obterPorStatusValido() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Veiculo> pagina = new PageImpl<>(Collections.singletonList(criarVeiculo(1L, "ABC1234", 0L)));
            when(veiculoRepository.findByStatus(StatusVeiculo.MANUTENCAO, pageable)).thenReturn(pagina);

            assertSame(pagina, veiculoService.obterVeiculosPorStatus("MANUTENCAO", pageable));
        }

        @Test
        void obterPorStatusInvalido() {
            StatusVeiculoInvalidoException ex = assertThrows(StatusVeiculoInvalidoException.class,
                    () -> veiculoService.obterVeiculosPorStatus("QUEBRADO", PageRequest.of(0, 10)));

            assertEquals("Status inválido: QUEBRADO", ex.getMessage());
            verifyNoInteractions(veiculoRepository);
        }

        @Test
        @DisplayName("B3: status nulo gera exceção de validação, não NPE")
        void obterPorStatusNulo() {
            assertThrows(StatusVeiculoInvalidoException.class, () -> veiculoService.obterVeiculosPorStatus(null, PageRequest.of(0, 10)));
        }
    }

    @Nested
    @DisplayName("registrarVeiculo")
    class RegistrarVeiculo {

        @Test
        void sucesso() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.registrarVeiculo("abc1234", "Scania R450", 2021);

            ArgumentCaptor<Veiculo> captor = ArgumentCaptor.forClass(Veiculo.class);
            verify(veiculoRepository).save(captor.capture());
            Veiculo salvo = captor.getValue();
            assertEquals("ABC1234", salvo.getPlaca());
            assertEquals("Scania R450", salvo.getModelo());
            assertEquals(2021, salvo.getAnoFabricacao());
            assertEquals(StatusVeiculo.ATIVO, salvo.getStatus());
            assertEquals(0L, salvo.getQuilometragem());
            assertNotNull(salvo.getDataCadastro());
            assertNotNull(salvo.getDataAtualizacao());
            assertSame(salvo, resultado);
            verify(notificador).notificarNovoVeiculo(salvo);
        }

        @Test
        @DisplayName("B1: placa em minúsculas é detectada como duplicada de placa maiúscula")
        void duplicidadeIgnoraCaixa() {
            when(veiculoRepository.findByPlaca("ABC1234"))
                    .thenReturn(Optional.of(criarVeiculo(1L, "ABC1234", 0L)));

            PlacaDuplicadaException ex = assertThrows(PlacaDuplicadaException.class,
                    () -> veiculoService.registrarVeiculo("abc1234", "Modelo", 2020));

            assertEquals("Veículo com placa ABC1234 já existe", ex.getMessage());
            verify(veiculoRepository, never()).save(any());
            verifyNoInteractions(notificador);
        }

        @Test
        @DisplayName("R1: corrida na criacao (UNIQUE do banco) vira PlacaDuplicadaException")
        void violacaoDeUniqueViraPlacaDuplicada() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());
            when(veiculoRepository.save(any(Veiculo.class))).thenThrow(new DataIntegrityViolationException("uk_placa"));

            PlacaDuplicadaException ex = assertThrows(PlacaDuplicadaException.class,
                    () -> veiculoService.registrarVeiculo("ABC1234", "Modelo", 2020));

            assertEquals("Veículo com placa ABC1234 já existe", ex.getMessage());
            verifyNoInteractions(notificador);
        }

        @Test
        void placaNula() {
            PlacaInvalidaException ex = assertThrows(PlacaInvalidaException.class,
                    () -> veiculoService.registrarVeiculo(null, "Modelo", 2020));

            assertEquals("Placa é obrigatória", ex.getMessage());
        }

        @Test
        void placaTamanhoIncorreto() {
            PlacaInvalidaException ex = assertThrows(PlacaInvalidaException.class,
                    () -> veiculoService.registrarVeiculo("ABC12", "Modelo", 2020));

            assertEquals("Placa deve ter 7 caracteres", ex.getMessage());
        }

        @Test
        @DisplayName("valida placa, modelo e ano antes de consultar duplicidade")
        void validaAntesDeConsultarRepositorio() {
            assertThrows(ModeloInvalidoException.class,
                    () -> veiculoService.registrarVeiculo("ABC1234", null, 2020));
            assertThrows(AnoFabricacaoInvalidoException.class,
                    () -> veiculoService.registrarVeiculo("ABC1234", "Modelo", 1899));

            verifyNoInteractions(veiculoRepository, notificador);
        }
    }

    @Nested
    @DisplayName("atualizarVeiculo")
    class AtualizarVeiculo {

        @Test
        void atualizaTodosOsCampos() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, "Mercedes Actros", 2022, 2000L);

            assertEquals("Mercedes Actros", resultado.getModelo());
            assertEquals(2022, resultado.getAnoFabricacao());
            assertEquals(2000L, resultado.getQuilometragem());
            assertNotNull(resultado.getDataAtualizacao());
        }

        @Test
        void ignoraCamposNulosOuVazios() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, "", null, null);

            assertEquals("Volvo FH", resultado.getModelo());
            assertEquals(2020, resultado.getAnoFabricacao());
            assertEquals(1000L, resultado.getQuilometragem());
        }

        @Test
        void ignoraModeloNulo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            assertEquals("Volvo FH", veiculoService.atualizarVeiculo(1L, null, 2019, null).getModelo());
        }

        @Test
        @DisplayName("B4: rejeita ano fora do intervalo")
        void rejeitaAnoInvalido() {
            veiculoExiste(criarVeiculo(1L, "ABC1234", 1000L));

            assertThrows(AnoFabricacaoInvalidoException.class,
                    () -> veiculoService.atualizarVeiculo(1L, null, 3000, null));
            verify(veiculoRepository, never()).save(any());
        }

        @Test
        @DisplayName("B4: rejeita quilometragem negativa")
        void rejeitaQuilometragemNegativa() {
            veiculoExiste(criarVeiculo(1L, "ABC1234", 1000L));

            assertThrows(QuilometragemInvalidaException.class,
                    () -> veiculoService.atualizarVeiculo(1L, null, null, -5L));
            verify(veiculoRepository, never()).save(any());
        }

        @Test
        @DisplayName("B4: rejeita modelo só com espaços")
        void rejeitaModeloEmBranco() {
            veiculoExiste(criarVeiculo(1L, "ABC1234", 1000L));

            assertThrows(ModeloInvalidoException.class,
                    () -> veiculoService.atualizarVeiculo(1L, "   ", null, null));
        }

        @Test
        @DisplayName("B5: redução de quilometragem continua permitida (apenas aviso em log)")
        void reducaoDeQuilometragem() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 5000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            assertEquals(100L, veiculoService.atualizarVeiculo(1L, null, null, 100L).getQuilometragem());
        }

        @Test
        @DisplayName("B2: quilometragem nula na entidade não causa NPE")
        void quilometragemNulaNaEntidade() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", null);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            assertEquals(300L, veiculoService.atualizarVeiculo(1L, null, null, 300L).getQuilometragem());
        }

        @Test
        void veiculoInexistente() {
            when(veiculoRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(VeiculoNaoEncontradoException.class,
                    () -> veiculoService.atualizarVeiculo(1L, "X", 2020, 1L));
            verify(veiculoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("atualizarQuilometragem")
    class AtualizarQuilometragem {

        @Test
        @DisplayName("B3: quilometragem nula gera exceção de validação, não NPE")
        void nula() {
            QuilometragemInvalidaException ex = assertThrows(QuilometragemInvalidaException.class,
                    () -> veiculoService.atualizarQuilometragem(1L, null));

            assertEquals("Quilometragem é obrigatória", ex.getMessage());
        }

        @Test
        void negativa() {
            QuilometragemInvalidaException ex = assertThrows(QuilometragemInvalidaException.class,
                    () -> veiculoService.atualizarQuilometragem(1L, -1L));

            assertEquals("Quilometragem não pode ser negativa", ex.getMessage());
            verify(veiculoRepository, never()).save(any());
        }

        @Test
        void abaixoDoLimiteNaoNotifica() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 100L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();
            when(manutencaoService.cruzouLimite(100L, 200L)).thenReturn(false);

            assertEquals(200L, veiculoService.atualizarQuilometragem(1L, 200L).getQuilometragem());
            verifyNoInteractions(notificador);
        }

        @Test
        void reducaoPermitidaComAviso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            assertEquals(500L, veiculoService.atualizarQuilometragem(1L, 500L).getQuilometragem());
        }

        @Test
        @DisplayName("B2: quilometragem atual nula não causa NPE")
        void quilometragemAtualNula() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", null);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            assertEquals(10L, veiculoService.atualizarQuilometragem(1L, 10L).getQuilometragem());
        }

        @Test
        @DisplayName("R3: nao repete o alerta se o veiculo ja estava acima do limite")
        void naoRepeteAlertaAcimaDoLimite() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 60000L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();
            when(manutencaoService.cruzouLimite(60000L, 70000L)).thenReturn(false);

            veiculoService.atualizarQuilometragem(1L, 70000L);

            verifyNoInteractions(notificador);
        }

        @Test
        @DisplayName("R3: notifica ao cruzar o limite, sem recarregar o veiculo (D10)")
        void notificaQuandoPrecisaDeManutencao() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 100L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();
            when(manutencaoService.cruzouLimite(100L, 50000L)).thenReturn(true);

            veiculoService.atualizarQuilometragem(1L, 50000L);

            verify(notificador).notificarAlertaManutencao(veiculo);
            verify(veiculoRepository, org.mockito.Mockito.times(1)).findById(1L);
        }
    }

    @Nested
    @DisplayName("Ativação e desativação")
    class AtivacaoDesativacao {

        @Test
        void desativar() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.desativarVeiculo(1L);

            assertEquals(StatusVeiculo.INATIVO, resultado.getStatus());
            assertNotNull(resultado.getDataAtualizacao());
            verify(notificador).notificarDesativacao(resultado);
        }

        @Test
        @DisplayName("R5: nao desativa veiculo em manutencao")
        void desativarEmManutencaoLancaConflito() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculo.setStatus(StatusVeiculo.MANUTENCAO);
            veiculoExiste(veiculo);

            TransicaoStatusInvalidaException ex = assertThrows(TransicaoStatusInvalidaException.class,
                    () -> veiculoService.desativarVeiculo(1L));

            assertEquals("Transição de status inválida: MANUTENCAO -> INATIVO", ex.getMessage());
            assertEquals(StatusVeiculo.MANUTENCAO, veiculo.getStatus());
            verify(veiculoRepository, never()).save(any());
            verifyNoInteractions(notificador);
        }

        @Test
        @DisplayName("desativar veiculo ja inativo e idempotente: sem gravacao nem notificacao")
        void desativarJaInativo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculo.setStatus(StatusVeiculo.INATIVO);
            veiculoExiste(veiculo);

            assertSame(veiculo, veiculoService.desativarVeiculo(1L));
            verify(veiculoRepository, never()).save(any());
            verifyNoInteractions(notificador);
        }

        @Test
        @DisplayName("reativar veiculo ja ativo nao grava")
        void reativarJaAtivo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculoExiste(veiculo);

            assertSame(veiculo, veiculoService.reativarVeiculo(1L));
            verify(veiculoRepository, never()).save(any());
        }

        @Test
        @DisplayName("alterarStatus valida a transicao e persiste")
        void alterarStatus() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.alterarStatus(veiculo, StatusVeiculo.MANUTENCAO);

            assertEquals(StatusVeiculo.MANUTENCAO, resultado.getStatus());
            assertNotNull(resultado.getDataAtualizacao());
            assertThrows(TransicaoStatusInvalidaException.class,
                    () -> veiculoService.alterarStatus(veiculo, StatusVeiculo.INATIVO));
        }

        @Test
        @DisplayName("alterarStatus aceita veiculo legado com status nulo")
        void alterarStatusDeNulo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculo.setStatus(null);
            saveRetornaArgumento();

            assertEquals(StatusVeiculo.INATIVO,
                    veiculoService.alterarStatus(veiculo, StatusVeiculo.INATIVO).getStatus());
        }

        @Test
        void reativar() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 0L);
            veiculo.setStatus(StatusVeiculo.INATIVO);
            veiculoExiste(veiculo);
            saveRetornaArgumento();

            Veiculo resultado = veiculoService.reativarVeiculo(1L);

            assertEquals(StatusVeiculo.ATIVO, resultado.getStatus());
            assertNotNull(resultado.getDataAtualizacao());
        }
    }
}
