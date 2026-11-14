package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.QuilometragemInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VeiculoManutencaoServiceTest {

    @Mock
    private VeiculoNotificador notificador;

    @InjectMocks
    private VeiculoManutencaoService service;

    private Veiculo veiculoComKm(Long km) {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca("ABC1234");
        veiculo.setQuilometragem(km);
        return veiculo;
    }

    @Test
    void precisaDeManutencaoNoLimite() {
        assertTrue(service.precisaDeManutencao(veiculoComKm(50000L)));
    }

    @Test
    void naoPrecisaAbaixoDoLimite() {
        assertFalse(service.precisaDeManutencao(veiculoComKm(49999L)));
    }

    @Test
    void naoPrecisaComQuilometragemNula() {
        assertFalse(service.precisaDeManutencao(veiculoComKm(null)));
    }

    @Test
    void cruzouLimiteApenasNaTransicao() {
        assertTrue(service.cruzouLimite(49999L, 50000L));
        assertTrue(service.cruzouLimite(null, 50000L));
        assertFalse(service.cruzouLimite(50000L, 60000L), "já estava acima do limite");
        assertFalse(service.cruzouLimite(100L, 200L), "continua abaixo do limite");
        assertFalse(service.cruzouLimite(60000L, 100L), "voltou para baixo do limite");
        assertFalse(service.cruzouLimite(100L, null));
    }

    @Test
    void calculaCustoComBigDecimal() {
        assertEquals(new BigDecimal("1500.00"), service.calcularCustoManutencao(20000L));
        assertEquals(new BigDecimal("500.00"), service.calcularCustoManutencao(0L));
    }

    @Test
    void custoRejeitaQuilometragemInvalida() {
        assertThrows(QuilometragemInvalidaException.class, () -> service.calcularCustoManutencao(null));
        assertThrows(QuilometragemInvalidaException.class, () -> service.calcularCustoManutencao(-1L));
    }

    @Test
    void agendarNotificaGestor() {
        Veiculo veiculo = veiculoComKm(1000L);

        service.agendarManutencaoPreventiva(veiculo, 60000L);

        verify(notificador).notificarManutencaoAgendada(veiculo, 60000L);
    }
}
