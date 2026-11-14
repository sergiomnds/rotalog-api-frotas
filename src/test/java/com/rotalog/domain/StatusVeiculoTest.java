package com.rotalog.domain;

import com.rotalog.exception.StatusVeiculoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusVeiculoTest {

    @Test
    void converteValoresValidos() {
        assertEquals(StatusVeiculo.ATIVO, StatusVeiculo.from("ATIVO"));
        assertEquals(StatusVeiculo.INATIVO, StatusVeiculo.from("INATIVO"));
        assertEquals(StatusVeiculo.MANUTENCAO, StatusVeiculo.from("MANUTENCAO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo", " ATIVO", "QUEBRADO", ""})
    void rejeitaValoresInvalidos(String valor) {
        StatusVeiculoInvalidoException ex = assertThrows(StatusVeiculoInvalidoException.class,
                () -> StatusVeiculo.from(valor));
        assertEquals("Status inválido: " + valor, ex.getMessage());
    }

    @Test
    void rejeitaNulo() {
        assertThrows(StatusVeiculoInvalidoException.class, () -> StatusVeiculo.from(null));
    }

    @Test
    void transicoesPermitidas() {
        assertTrue(StatusVeiculo.ATIVO.podeTransitarPara(StatusVeiculo.INATIVO));
        assertTrue(StatusVeiculo.ATIVO.podeTransitarPara(StatusVeiculo.MANUTENCAO));
        assertTrue(StatusVeiculo.INATIVO.podeTransitarPara(StatusVeiculo.ATIVO));
        assertTrue(StatusVeiculo.INATIVO.podeTransitarPara(StatusVeiculo.MANUTENCAO));
        assertTrue(StatusVeiculo.MANUTENCAO.podeTransitarPara(StatusVeiculo.ATIVO));
    }

    @Test
    void manutencaoNaoPodeIrDiretoParaInativo() {
        assertFalse(StatusVeiculo.MANUTENCAO.podeTransitarPara(StatusVeiculo.INATIVO));
    }

    @Test
    void mesmoStatusEIdempotente() {
        for (StatusVeiculo status : StatusVeiculo.values()) {
            assertTrue(status.podeTransitarPara(status));
        }
    }
}
