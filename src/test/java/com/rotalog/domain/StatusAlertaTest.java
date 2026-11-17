package com.rotalog.domain;

import com.rotalog.exception.StatusAlertaInvalidoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusAlertaTest {

    @Test
    void converteTextoNoStatusCorrespondente() {
        assertEquals(StatusAlerta.PENDENTE, StatusAlerta.from("PENDENTE"));
        assertEquals(StatusAlerta.ENVIADO, StatusAlerta.from("ENVIADO"));
        assertEquals(StatusAlerta.FALHA, StatusAlerta.from("FALHA"));
    }

    @Test
    void rejeitaValorInvalidoNuloOuEmMinusculas() {
        assertThrows(StatusAlertaInvalidoException.class, () -> StatusAlerta.from("XPTO"));
        assertThrows(StatusAlertaInvalidoException.class, () -> StatusAlerta.from("pendente"));
        assertThrows(StatusAlertaInvalidoException.class, () -> StatusAlerta.from(null));
    }
}
