package com.rotalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Alerta de manutenção preventiva gerado para um veículo, com o status da notificação ao gestor.
 */
@Entity
@Table(name = "alertas_manutencao")
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    /** Placa copiada no momento do alerta, para listar sem consultar o veículo. */
    @Column(name = "placa", nullable = false)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false)
    private MotivoAlerta motivo;

    @Column(name = "mensagem", nullable = false)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusAlerta status;

    /** Motivo da última tentativa de envio malsucedida (null quando enviado). */
    @Column(name = "detalhe_erro")
    private String detalheErro;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;
}
