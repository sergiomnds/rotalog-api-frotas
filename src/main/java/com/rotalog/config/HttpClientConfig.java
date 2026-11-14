package com.rotalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * RestTemplate da api-notificacoes com timeouts: se o serviço travar, a requisição de veículo
     * falha rápido (e a falha de notificação já é tratada sem interromper o fluxo).
     */
    @Bean("notificacaoRestTemplate")
    public RestTemplate notificacaoRestTemplate(
            RestTemplateBuilder builder,
            @Value("${rotalog.api.notificacoes.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${rotalog.api.notificacoes.read-timeout-ms:5000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
