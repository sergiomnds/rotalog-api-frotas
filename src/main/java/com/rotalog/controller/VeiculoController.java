package com.rotalog.controller;

import com.rotalog.domain.Veiculo;
import com.rotalog.dto.EstatisticasFrotaResponse;
import com.rotalog.dto.VeiculoRequest;
import com.rotalog.dto.VeiculoResponse;
import com.rotalog.service.VeiculoEstatisticasService;
import com.rotalog.service.VeiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VeiculoController - REST controller para gerenciamento de veículos.
 * Erros são traduzidos em HTTP pelo {@link GlobalExceptionHandler}.
 *
 * FIXME: Sem @Valid no request body
 * FIXME: Sem URI no header Location
 */
@RestController
@RequestMapping("/veiculos")
@RequiredArgsConstructor
public class VeiculoController {

    private static final String HEADER_TOTAL = "X-Total-Count";
    private static final String HEADER_PAGINAS = "X-Total-Pages";

    private final VeiculoService veiculoService;
    private final VeiculoEstatisticasService estatisticasService;

    /**
     * Lista veículos. O corpo continua sendo um array; a paginação é controlada por
     * ?page=0&size=100&sort=id e o total vem nos headers X-Total-Count e X-Total-Pages.
     */
    @GetMapping
    public ResponseEntity<List<VeiculoResponse>> listarTodos(
            @PageableDefault(size = 100, sort = "id") Pageable pageable) {
        return paginaComoLista(veiculoService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(VeiculoResponse.from(veiculoService.buscarPorId(id)));
    }

    @GetMapping("/placa/{placa}")
    public ResponseEntity<VeiculoResponse> buscarPorPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(VeiculoResponse.from(veiculoService.buscarPorPlaca(placa)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<VeiculoResponse>> listarPorStatus(
            @PathVariable String status,
            @PageableDefault(size = 100, sort = "id") Pageable pageable) {
        return paginaComoLista(veiculoService.obterVeiculosPorStatus(status, pageable));
    }

    @PostMapping
    public ResponseEntity<VeiculoResponse> registrarVeiculo(@RequestBody VeiculoRequest request) {
        Veiculo veiculo = veiculoService.registrarVeiculo(
                request.getPlaca(), request.getModelo(), request.getAnoFabricacao());
        return ResponseEntity.status(HttpStatus.CREATED).body(VeiculoResponse.from(veiculo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeiculoResponse> atualizarVeiculo(@PathVariable Long id,
                                                            @RequestBody VeiculoRequest request) {
        Veiculo veiculo = veiculoService.atualizarVeiculo(
                id, request.getModelo(), request.getAnoFabricacao(), request.getQuilometragem());
        return ResponseEntity.ok(VeiculoResponse.from(veiculo));
    }

    @PatchMapping("/{id}/quilometragem")
    public ResponseEntity<VeiculoResponse> atualizarQuilometragem(@PathVariable Long id,
                                                                  @RequestBody Map<String, Long> body) {
        Veiculo veiculo = veiculoService.atualizarQuilometragem(id, body.get("quilometragem"));
        return ResponseEntity.ok(VeiculoResponse.from(veiculo));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<VeiculoResponse> desativarVeiculo(@PathVariable Long id) {
        return ResponseEntity.ok(VeiculoResponse.from(veiculoService.desativarVeiculo(id)));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<VeiculoResponse> reativarVeiculo(@PathVariable Long id) {
        return ResponseEntity.ok(VeiculoResponse.from(veiculoService.reativarVeiculo(id)));
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<EstatisticasFrotaResponse> obterEstatisticas() {
        return ResponseEntity.ok(estatisticasService.obterEstatisticas());
    }

    private ResponseEntity<List<VeiculoResponse>> paginaComoLista(Page<Veiculo> pagina) {
        List<VeiculoResponse> corpo = pagina.getContent().stream()
                .map(VeiculoResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok()
                .header(HEADER_TOTAL, String.valueOf(pagina.getTotalElements()))
                .header(HEADER_PAGINAS, String.valueOf(pagina.getTotalPages()))
                .body(corpo);
    }
}
