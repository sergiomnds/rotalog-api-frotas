package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.service.ManutencaoPreventivaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alertas de manutenção preventiva: disparo da verificação, reprocessamento dos pendentes e listagem
 * (com filtro opcional por status) usada pelo painel-admin.
 */
@RestController
@RequestMapping("/alertas-manutencao")
public class AlertaManutencaoController {

    private final ManutencaoPreventivaService service;

    public AlertaManutencaoController(ManutencaoPreventivaService service) {
        this.service = service;
    }

    /** GET /alertas-manutencao?status=PENDENTE|ENVIADO|FALHA */
    @GetMapping
    public List<AlertaManutencaoResponse> listar(@RequestParam(required = false) String status) {
        StatusAlerta filtro = status == null || status.isEmpty() ? null : StatusAlerta.from(status);
        return converter(service.listar(filtro));
    }

    /** Verifica os veículos ativos e gera alertas para os elegíveis. Retorna os alertas gerados. */
    @PostMapping("/verificacao")
    public List<AlertaManutencaoResponse> verificar() {
        return converter(service.verificarEAlertar());
    }

    /** Reenvia os alertas pendentes. Retorna os alertas reprocessados com o status atualizado. */
    @PostMapping("/reprocessamento")
    public List<AlertaManutencaoResponse> reprocessar() {
        return converter(service.reprocessarPendentes());
    }

    private List<AlertaManutencaoResponse> converter(List<AlertaManutencao> alertas) {
        return alertas.stream().map(AlertaManutencaoResponse::from).collect(Collectors.toList());
    }
}
