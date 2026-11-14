package com.rotalog.domain;

import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Version;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica os metadados de mapeamento que o schema (ddl-auto=validate) e o banco esperam.
 * Não substitui um teste de integração com PostgreSQL.
 */
class VeiculoMapeamentoTest {

    @Test
    void versaoOtimistaMapeadaParaColunaVersao() throws Exception {
        Field campo = Veiculo.class.getDeclaredField("versao");

        assertNotNull(campo.getAnnotation(Version.class));
        assertEquals("versao", campo.getAnnotation(Column.class).name());
        assertEquals(Long.class, campo.getType());
    }

    @Test
    void statusMapeadoComoStringDoEnum() throws Exception {
        Field campo = Veiculo.class.getDeclaredField("status");

        assertEquals(EnumType.STRING, campo.getAnnotation(Enumerated.class).value());
        assertTrue(StatusVeiculo.class.isAssignableFrom(campo.getType()));
    }
}
