package com.ticketing.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(5));
                    setReadTimeout(Duration.ofSeconds(10));
                }})
                .build();
    }
}
