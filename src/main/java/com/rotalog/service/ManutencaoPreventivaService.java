package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Alerta de manutenção preventiva.
 * <p>
 * Um veículo ATIVO é elegível quando, desde a última manutenção concluída (ou, se nunca houve, desde o
 * cadastro), rodou mais que o limite de km OU passou mais que o limite de meses.
 * <p>
 * O alerta é gravado como PENDENTE antes da chamada HTTP síncrona à api-notificacoes, de modo que nunca se
 * perde: se a chamada funcionar vira ENVIADO; se o serviço estiver fora do ar (timeout, conexão recusada,
 * 5xx) permanece PENDENTE para reprocessamento; se o serviço rejeitar o pedido (4xx) vira FALHA.
 * Nenhuma transação de banco fica aberta durante a chamada HTTP.
 */
@Slf4j
@Service
public class ManutencaoPreventivaService {

    private static final String STATUS_MANUTENCAO_CONCLUIDA = "CONCLUIDA";
    private static final LocalDateTime INICIO_DOS_TEMPOS = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int TAMANHO_MAXIMO_ERRO = 500;

    private final VeiculoRepository veiculoRepository;
    private final ManutencaoRepository manutencaoRepository;
    private final AlertaManutencaoRepository alertaRepository;
    private final NotificacaoClient notificacaoClient;
    private final long limiteKm;
    private final int limiteMeses;
    private final String destinatario;

    public ManutencaoPreventivaService(VeiculoRepository veiculoRepository,
                                       ManutencaoRepository manutencaoRepository,
                                       AlertaManutencaoRepository alertaRepository,
                                       NotificacaoClient notificacaoClient,
                                       @Value("${rotalog.manutencao.preventiva.limite-km:10000}") long limiteKm,
                                       @Value("${rotalog.manutencao.preventiva.limite-meses:6}") int limiteMeses,
                                       @Value("${rotalog.notificacao.gestor:gestor@rotalog.com}") String destinatario) {
        this.veiculoRepository = veiculoRepository;
        this.manutencaoRepository = manutencaoRepository;
        this.alertaRepository = alertaRepository;
        this.notificacaoClient = notificacaoClient;
        this.limiteKm = limiteKm;
        this.limiteMeses = limiteMeses;
        this.destinatario = destinatario;
    }

    /**
     * Avalia todos os veículos ativos e gera (e tenta enviar) um alerta para cada elegível que ainda não
     * tenha alerta pendente/enviado desde a última manutenção.
     *
     * @return os alertas gerados nesta execução, já com o status da notificação
     */
    public List<AlertaManutencao> verificarEAlertar() {
        LocalDateTime agora = LocalDateTime.now();
        List<AlertaManutencao> gerados = new ArrayList<>();

        for (Veiculo veiculo : veiculoRepository.findAllByStatus(StatusVeiculo.ATIVO)) {
            Optional<Manutencao> ultima = manutencaoRepository
                    .findFirstByVeiculoIdAndStatusAndDataManutencaoIsNotNullOrderByDataManutencaoDesc(
                            veiculo.getId(), STATUS_MANUTENCAO_CONCLUIDA);
            LocalDateTime dataReferencia = dataReferencia(veiculo, ultima);

            Optional<MotivoAlerta> motivo = avaliar(veiculo, ultima, dataReferencia, agora);
            if (!motivo.isPresent()) {
                continue;
            }
            if (alertaRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(
                    veiculo.getId(), Arrays.asList(StatusAlerta.PENDENTE, StatusAlerta.ENVIADO),
                    dataReferenciaParaConsulta(ultima))) {
                log.debug("Veículo {} já possui alerta desde a última manutenção", veiculo.getPlaca());
                continue;
            }

            AlertaManutencao alerta = novoAlerta(veiculo, motivo.get(), agora);
            alerta = alertaRepository.save(alerta);
            tentarEnviar(alerta);
            gerados.add(alerta);
        }

        log.info("Verificação de manutenção preventiva concluída: {} alerta(s) gerado(s)", gerados.size());
        return gerados;
    }

    /** Reenvia os alertas PENDENTE (ex.: a api-notificacoes voltou ao ar). */
    public List<AlertaManutencao> reprocessarPendentes() {
        List<AlertaManutencao> pendentes =
                alertaRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE);
        for (AlertaManutencao alerta : pendentes) {
            tentarEnviar(alerta);
        }
        log.info("Reprocessamento de alertas pendentes: {} tentado(s)", pendentes.size());
        return pendentes;
    }

    /** Lista os alertas, do mais recente ao mais antigo; com status, apenas os dessa situação. */
    public List<AlertaManutencao> listar(StatusAlerta status) {
        return status == null
                ? alertaRepository.findAllByOrderByDataCriacaoDesc()
                : alertaRepository.findByStatusOrderByDataCriacaoDesc(status);
    }

    private Optional<MotivoAlerta> avaliar(Veiculo veiculo, Optional<Manutencao> ultima,
                                           LocalDateTime dataReferencia, LocalDateTime agora) {
        long kmReferencia = ultima.map(Manutencao::getQuilometragemManutencao).orElse(0L);
        long kmAtual = veiculo.getQuilometragem() == null ? 0L : veiculo.getQuilometragem();

        boolean porKm = kmAtual - kmReferencia > limiteKm;
        boolean porTempo = dataReferencia.isBefore(agora.minusMonths(limiteMeses));

        if (porKm && porTempo) {
            return Optional.of(MotivoAlerta.QUILOMETRAGEM_E_TEMPO);
        }
        if (porKm) {
            return Optional.of(MotivoAlerta.QUILOMETRAGEM);
        }
        if (porTempo) {
            return Optional.of(MotivoAlerta.TEMPO);
        }
        return Optional.empty();
    }

    private LocalDateTime dataReferencia(Veiculo veiculo, Optional<Manutencao> ultima) {
        if (ultima.isPresent()) {
            return ultima.get().getDataManutencao();
        }
        return veiculo.getDataCadastro() != null ? veiculo.getDataCadastro() : INICIO_DOS_TEMPOS;
    }

    private LocalDateTime dataReferenciaParaConsulta(Optional<Manutencao> ultima) {
        return ultima.map(Manutencao::getDataManutencao).orElse(INICIO_DOS_TEMPOS);
    }

    private AlertaManutencao novoAlerta(Veiculo veiculo, MotivoAlerta motivo, LocalDateTime agora) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setVeiculoId(veiculo.getId());
        alerta.setPlaca(veiculo.getPlaca());
        alerta.setMotivo(motivo);
        alerta.setMensagem(montarMensagem(veiculo, motivo));
        alerta.setStatus(StatusAlerta.PENDENTE);
        alerta.setTentativas(0);
        alerta.setDataCriacao(agora);
        return alerta;
    }

    private String montarMensagem(Veiculo veiculo, MotivoAlerta motivo) {
        String criterio;
        switch (motivo) {
            case QUILOMETRAGEM:
                criterio = "ultrapassou " + limiteKm + " km desde a última manutenção";
                break;
            case TEMPO:
                criterio = "está há mais de " + limiteMeses + " meses sem manutenção";
                break;
            default:
                criterio = "ultrapassou " + limiteKm + " km e está há mais de " + limiteMeses
                        + " meses sem manutenção";
        }
        return "Veículo " + veiculo.getPlaca() + " (" + veiculo.getModelo() + ", " + veiculo.getQuilometragem()
                + " km) " + criterio + ". Agendar manutenção preventiva.";
    }

    /** Chamada HTTP síncrona à api-notificacoes; atualiza o status do alerta conforme o resultado. */
    private void tentarEnviar(AlertaManutencao alerta) {
        alerta.setTentativas(alerta.getTentativas() + 1);
        try {
            notificacaoClient.enviarAlertaManutencao(alerta.getId(), alerta.getVeiculoId(), alerta.getPlaca(),
                    alerta.getMotivo().name(), destinatario, alerta.getMensagem());
            alerta.setStatus(StatusAlerta.ENVIADO);
            alerta.setDataEnvio(LocalDateTime.now());
            alerta.setDetalheErro(null);
        } catch (HttpClientErrorException e) {
            log.error("api-notificacoes rejeitou o alerta {} ({}): {}", alerta.getId(), alerta.getPlaca(),
                    e.getMessage());
            alerta.setStatus(StatusAlerta.FALHA);
            alerta.setDetalheErro(resumir(e));
        } catch (Exception e) {
            // Serviço fora do ar, timeout, 5xx etc.: o alerta fica registrado como pendente
            log.warn("api-notificacoes indisponível; alerta {} ({}) permanece PENDENTE: {}", alerta.getId(),
                    alerta.getPlaca(), e.getMessage());
            alerta.setStatus(StatusAlerta.PENDENTE);
            alerta.setDetalheErro(resumir(e));
        }
        alertaRepository.save(alerta);
    }

    private String resumir(Exception e) {
        String texto = e.getClass().getSimpleName() + ": " + e.getMessage();
        return texto.length() > TAMANHO_MAXIMO_ERRO ? texto.substring(0, TAMANHO_MAXIMO_ERRO) : texto;
    }
}
