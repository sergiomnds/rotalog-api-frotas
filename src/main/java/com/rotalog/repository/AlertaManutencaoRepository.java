package com.rotalog.repository;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface AlertaManutencaoRepository extends JpaRepository<AlertaManutencao, Long> {

    List<AlertaManutencao> findAllByOrderByDataCriacaoDesc();

    List<AlertaManutencao> findByStatusOrderByDataCriacaoDesc(StatusAlerta status);

    /** Evita alerta duplicado: já existe alerta em aberto/enviado para o veículo desde a última manutenção. */
    boolean existsByVeiculoIdAndStatusInAndDataCriacaoAfter(Long veiculoId,
                                                            Collection<StatusAlerta> status,
                                                            LocalDateTime desde);
}
