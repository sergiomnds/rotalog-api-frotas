package com.rotalog.service;

import com.rotalog.exception.AnoFabricacaoInvalidoException;
import com.rotalog.exception.ModeloInvalidoException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeiculoValidatorTest {

    private final VeiculoValidator validator = new VeiculoValidator();

    @Test
    void normalizarPlacaAplicaTrimEMaiusculas() {
        assertEquals("ABC1234", validator.normalizarPlaca("  abc1234 "));
    }

    @Test
    void normalizarPlacaNulaRetornaNulo() {
        assertNull(validator.normalizarPlaca(null));
    }

    @Test
    void padronizarPlacaValida() {
        assertEquals("ABC1D23", validator.padronizarPlaca(" abc1d23"));
    }

    @Test
    void padronizarPlacaNula() {
        assertEquals("Placa é obrigatória",
                assertThrows(PlacaInvalidaException.class, () -> validator.padronizarPlaca(null)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void padronizarPlacaVazia(String placa) {
        assertEquals("Placa é obrigatória",
                assertThrows(PlacaInvalidaException.class, () -> validator.padronizarPlaca(placa)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "ABC12345"})
    void padronizarPlacaTamanhoIncorreto(String placa) {
        assertEquals("Placa deve ter 7 caracteres",
                assertThrows(PlacaInvalidaException.class, () -> validator.padronizarPlaca(placa)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void modeloEmBranco(String modelo) {
        assertThrows(ModeloInvalidoException.class, () -> validator.validarModelo(modelo));
    }

    @Test
    void modeloNuloOuValido() {
        assertThrows(ModeloInvalidoException.class, () -> validator.validarModelo(null));
        assertDoesNotThrow(() -> validator.validarModelo("Volvo FH"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101})
    void anoForaDoIntervalo(int ano) {
        assertThrows(AnoFabricacaoInvalidoException.class, () -> validator.validarAnoFabricacao(ano));
    }

    @Test
    void anoNuloOuNosLimites() {
        assertThrows(AnoFabricacaoInvalidoException.class, () -> validator.validarAnoFabricacao(null));
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(1900));
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(2100));
    }

    @Test
    void quilometragemInvalidaOuValida() {
        assertThrows(QuilometragemInvalidaException.class, () -> validator.validarQuilometragem(null));
        assertThrows(QuilometragemInvalidaException.class, () -> validator.validarQuilometragem(-1L));
        assertDoesNotThrow(() -> validator.validarQuilometragem(0L));
    }
}
