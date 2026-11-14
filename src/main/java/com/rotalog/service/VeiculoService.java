package com.rotalog.service;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Casos de uso de veículo. Orquestra validação, notificação e regras de manutenção.
 * Todas as mudanças de status passam por {@link #alterarStatus(Veiculo, StatusVeiculo)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;
    private final VeiculoValidator validator;
    private final VeiculoNotificador notificador;
    private final VeiculoManutencaoService manutencaoService;

    public Page<Veiculo> listarTodos(Pageable pageable) {
        log.info("Listando veículos: página {}", pageable);
        return veiculoRepository.findAll(pageable);
    }

    public Veiculo buscarPorId(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> VeiculoNaoEncontradoException.porId(id));
    }

    public Veiculo buscarPorPlaca(String placa) {
        String placaNormalizada = validator.normalizarPlaca(placa);
        return veiculoRepository.findByPlaca(placaNormalizada)
                .orElseThrow(() -> VeiculoNaoEncontradoException.porPlaca(placaNormalizada));
    }

    public Veiculo registrarVeiculo(String placa, String modelo, Integer anoFabricacao) {
        String placaPadronizada = validator.padronizarPlaca(placa);
        validator.validarModelo(modelo);
        validator.validarAnoFabricacao(anoFabricacao);

        if (veiculoRepository.findByPlaca(placaPadronizada).isPresent()) {
            throw new PlacaDuplicadaException(placaPadronizada);
        }

        LocalDateTime agora = LocalDateTime.now();
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placaPadronizada);
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        veiculo.setQuilometragem(0L);
        veiculo.setDataCadastro(agora);
        veiculo.setDataAtualizacao(agora);

        Veiculo salvo = salvarNovo(veiculo);
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());

        notificador.notificarNovoVeiculo(salvo);
        return salvo;
    }

    public Veiculo atualizarVeiculo(Long id, String modelo, Integer anoFabricacao, Long quilometragem) {
        Veiculo veiculo = buscarPorId(id);

        if (modelo != null && !modelo.isEmpty()) {
            validator.validarModelo(modelo);
            veiculo.setModelo(modelo);
        }
        if (anoFabricacao != null) {
            validator.validarAnoFabricacao(anoFabricacao);
            veiculo.setAnoFabricacao(anoFabricacao);
        }
        if (quilometragem != null) {
            validator.validarQuilometragem(quilometragem);
            avisarSeQuilometragemReduzida(veiculo, quilometragem);
            veiculo.setQuilometragem(quilometragem);
        }

        veiculo.setDataAtualizacao(LocalDateTime.now());
        return veiculoRepository.save(veiculo);
    }

    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        validator.validarQuilometragem(novaQuilometragem);
        Veiculo veiculo = buscarPorId(veiculoId);

        Long quilometragemAnterior = veiculo.getQuilometragem();
        avisarSeQuilometragemReduzida(veiculo, novaQuilometragem);
        veiculo.setQuilometragem(novaQuilometragem);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo atualizado = veiculoRepository.save(veiculo);

        if (manutencaoService.cruzouLimite(quilometragemAnterior, atualizado.getQuilometragem())) {
            notificador.notificarAlertaManutencao(atualizado);
        }
        return atualizado;
    }

    public Page<Veiculo> obterVeiculosPorStatus(String status, Pageable pageable) {
        return veiculoRepository.findByStatus(StatusVeiculo.from(status), pageable);
    }

    public Veiculo desativarVeiculo(Long veiculoId) {
        Veiculo veiculo = buscarPorId(veiculoId);
        if (veiculo.getStatus() == StatusVeiculo.INATIVO) {
            log.info("Veículo {} já está inativo", veiculo.getPlaca());
            return veiculo;
        }

        Veiculo desativado = alterarStatus(veiculo, StatusVeiculo.INATIVO);
        log.info("Veículo desativado: {}", desativado.getPlaca());

        notificador.notificarDesativacao(desativado);
        return desativado;
    }

    public Veiculo reativarVeiculo(Long veiculoId) {
        Veiculo veiculo = alterarStatus(buscarPorId(veiculoId), StatusVeiculo.ATIVO);
        log.info("Veículo reativado: {}", veiculo.getPlaca());
        return veiculo;
    }

    /**
     * Ponto único de mudança de status. Valida a transição, persiste e devolve o veículo.
     * Pedir o status em que o veículo já está não faz nada.
     *
     * @throws TransicaoStatusInvalidaException se a transição não for permitida
     */
    public Veiculo alterarStatus(Veiculo veiculo, StatusVeiculo destino) {
        StatusVeiculo origem = veiculo.getStatus();
        if (origem == destino) {
            return veiculo;
        }
        if (origem != null && !origem.podeTransitarPara(destino)) {
            throw new TransicaoStatusInvalidaException(origem, destino);
        }
        veiculo.setStatus(destino);
        veiculo.setDataAtualizacao(LocalDateTime.now());
        return veiculoRepository.save(veiculo);
    }

    /**
     * A checagem de duplicidade acima não é atômica: se duas requisições passarem por ela ao mesmo tempo,
     * a segunda falha na restrição UNIQUE do banco, que aqui vira a mesma exceção de placa duplicada.
     */
    private Veiculo salvarNovo(Veiculo veiculo) {
        try {
            return veiculoRepository.save(veiculo);
        } catch (DataIntegrityViolationException e) {
            log.warn("Placa {} rejeitada pelo banco (corrida na criação): {}", veiculo.getPlaca(), e.getMessage());
            throw new PlacaDuplicadaException(veiculo.getPlaca());
        }
    }

    /**
     * Reduzir a quilometragem é permitido (correção de odômetro), mas fica registrado em log.
     */
    private void avisarSeQuilometragemReduzida(Veiculo veiculo, Long novaQuilometragem) {
        Long atual = veiculo.getQuilometragem();
        if (atual != null && novaQuilometragem < atual) {
            log.warn("Quilometragem do veículo {} reduzida: {} -> {}", veiculo.getId(), atual, novaQuilometragem);
        }
    }
}
