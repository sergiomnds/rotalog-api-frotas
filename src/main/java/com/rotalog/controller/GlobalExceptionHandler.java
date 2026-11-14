package com.rotalog.controller;

import com.rotalog.exception.FrotasException;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Collections;
import java.util.Map;

/**
 * Tradução central de exceções em respostas HTTP, no formato {"erro": "..."} já usado pela API.
 * Erros de negócio ({@link FrotasException}) expõem sua mensagem; erros de infraestrutura nunca:
 * viram uma mensagem genérica e o detalhe fica apenas no log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(VeiculoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(VeiculoNaoEncontradoException ex) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({PlacaDuplicadaException.class, TransicaoStatusInvalidaException.class})
    public ResponseEntity<Map<String, String>> conflitoDeNegocio(FrotasException ex) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(FrotasException.class)
    public ResponseEntity<Map<String, String>> erroDeNegocio(FrotasException ex) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> violacaoDeIntegridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade de dados: {}", ex.getMessage());
        return resposta(HttpStatus.CONFLICT, "Conflito com dados já existentes");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflitoDeConcorrencia(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência: {}", ex.getMessage());
        return resposta(HttpStatus.CONFLICT, "O registro foi alterado por outra requisição. Tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> erroInesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor");
    }

    /** Exceções padrão do Spring MVC (JSON inválido, método não suportado etc.) mantêm o status e o formato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        Map<String, String> corpo = Collections.singletonMap("erro", status.getReasonPhrase());
        return super.handleExceptionInternal(ex, corpo, headers, status, request);
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Collections.singletonMap("erro", mensagem));
    }
}
