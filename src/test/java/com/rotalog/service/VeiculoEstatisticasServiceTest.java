package com.rotalog.service;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.dto.EstatisticasFrotaResponse;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoEstatisticasServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @InjectMocks
    private VeiculoEstatisticasService service;

    @Test
    void obterEstatisticasUsaContagemNoBanco() {
        when(veiculoRepository.count()).thenReturn(6L);
        when(veiculoRepository.countByStatus(StatusVeiculo.ATIVO)).thenReturn(3L);
        when(veiculoRepository.countByStatus(StatusVeiculo.INATIVO)).thenReturn(2L);
        when(veiculoRepository.countByStatus(StatusVeiculo.MANUTENCAO)).thenReturn(1L);

        EstatisticasFrotaResponse resposta = service.obterEstatisticas();

        assertEquals(6L, resposta.getTotal());
        assertEquals(3L, resposta.getAtivos());
        assertEquals(2L, resposta.getInativos());
        assertEquals(1L, resposta.getEmManutencao());
        verify(veiculoRepository, times(3)).countByStatus(any());
        verifyNoMoreInteractions(veiculoRepository);
    }
}
