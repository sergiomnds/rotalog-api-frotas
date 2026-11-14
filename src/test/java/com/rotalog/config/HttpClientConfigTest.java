package com.rotalog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientConfigTest {

    @Test
    void restTemplateDeNotificacaoTemTimeoutsConfigurados() throws Exception {
        RestTemplate restTemplate = new HttpClientConfig()
                .notificacaoRestTemplate(new RestTemplateBuilder(), 1500, 3500);

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertTrue(factory instanceof SimpleClientHttpRequestFactory, "factory: " + factory.getClass());
        assertEquals(1500, campo(factory, "connectTimeout"));
        assertEquals(3500, campo(factory, "readTimeout"));
    }

    private Object campo(Object alvo, String nome) throws Exception {
        Field field = alvo.getClass().getDeclaredField(nome);
        field.setAccessible(true);
        return field.get(alvo);
    }
}
