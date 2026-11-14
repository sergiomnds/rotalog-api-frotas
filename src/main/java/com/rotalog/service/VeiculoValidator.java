package com.rotalog.service;

import com.rotalog.exception.AnoFabricacaoInvalidoException;
import com.rotalog.exception.ModeloInvalidoException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import org.springframework.stereotype.Component;

/**
 * Validação e padronização dos dados de entrada de veículo.
 */
@Component
public class VeiculoValidator {

    private static final int TAMANHO_PLACA = 7;
    private static final int ANO_MINIMO = 1900;
    private static final int ANO_MAXIMO = 2100;

    /**
     * Remove espaços e converte para maiúsculas, sem validar. Retorna null se a placa for nula.
     */
    public String normalizarPlaca(String placa) {
        return placa == null ? null : placa.trim().toUpperCase();
    }

    /**
     * Normaliza e valida a placa, devolvendo o valor padronizado.
     */
    public String padronizarPlaca(String placa) {
        String normalizada = normalizarPlaca(placa);
        if (normalizada == null || normalizada.isEmpty()) {
            throw new PlacaInvalidaException("Placa é obrigatória");
        }
        if (normalizada.length() != TAMANHO_PLACA) {
            throw new PlacaInvalidaException("Placa deve ter " + TAMANHO_PLACA + " caracteres");
        }
        return normalizada;
    }

    public void validarModelo(String modelo) {
        if (modelo == null || modelo.trim().isEmpty()) {
            throw new ModeloInvalidoException("Modelo é obrigatório");
        }
    }

    public void validarAnoFabricacao(Integer ano) {
        if (ano == null || ano < ANO_MINIMO || ano > ANO_MAXIMO) {
            throw new AnoFabricacaoInvalidoException("Ano de fabricação inválido");
        }
    }

    public void validarQuilometragem(Long quilometragem) {
        if (quilometragem == null) {
            throw new QuilometragemInvalidaException("Quilometragem é obrigatória");
        }
        if (quilometragem < 0) {
            throw new QuilometragemInvalidaException("Quilometragem não pode ser negativa");
        }
    }
}
