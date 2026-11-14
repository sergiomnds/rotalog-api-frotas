package com.rotalog.service;

import com.rotalog.domain.Manutencao;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Garante que o ManutencaoService muda o status do veículo apenas pelo ponto único (VeiculoService). */
@ExtendWith(MockitoExtension.class)
class ManutencaoServiceStatusVeiculoTest {

    @Mock
    private ManutencaoRepository manutencaoRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    @Mock
    private VeiculoService veiculoService;

    @InjectMocks
    private ManutencaoService service;

    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(1L);
        manutencao.setStatus("PENDENTE");
        veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setStatus(StatusVeiculo.ATIVO);
        when(manutencaoRepository.findById(10L)).thenReturn(Optional.of(manutencao));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        lenient().when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void iniciarColocaVeiculoEmManutencao() {
        service.iniciarManutencao(10L);

        verify(veiculoService).alterarStatus(veiculo, StatusVeiculo.MANUTENCAO);
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void concluirReativaVeiculo() {
        service.concluirManutencao(10L, new BigDecimal("100"));

        verify(veiculoService).alterarStatus(veiculo, StatusVeiculo.ATIVO);
    }

    @Test
    void cancelarReativaVeiculoEmManutencao() {
        veiculo.setStatus(StatusVeiculo.MANUTENCAO);

        service.cancelarManutencao(10L);

        verify(veiculoService).alterarStatus(veiculo, StatusVeiculo.ATIVO);
    }

    @Test
    void cancelarNaoAlteraVeiculoQueNaoEstaEmManutencao() {
        veiculo.setStatus(StatusVeiculo.INATIVO);

        service.cancelarManutencao(10L);

        verify(veiculoService, never()).alterarStatus(any(), any());
    }

    @Test
    void transicaoInvalidaImpedeIniciarManutencao() {
        when(veiculoService.alterarStatus(veiculo, StatusVeiculo.MANUTENCAO))
                .thenThrow(new TransicaoStatusInvalidaException(StatusVeiculo.ATIVO, StatusVeiculo.MANUTENCAO));

        assertThrows(TransicaoStatusInvalidaException.class, () -> service.iniciarManutencao(10L));
        verify(manutencaoRepository, never()).save(any());
    }
}
