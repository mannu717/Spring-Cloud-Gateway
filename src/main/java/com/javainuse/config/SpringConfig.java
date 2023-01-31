package com.javainuse.config;

import brave.http.HttpTracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.brave.ReactorNettyHttpTracing;
import reactor.netty.http.client.HttpClient;

@Configuration
public class SpringConfig {

    final Logger logger = LoggerFactory.getLogger(SpringConfig.class);

    @Bean
    public ReactorNettyHttpTracing reactorNettyHttpTracing(HttpTracing httpTracing) {
        return ReactorNettyHttpTracing.create(httpTracing);
    }

    @Bean
    public WebClient webClient(ReactorNettyHttpTracing tracing) {
        HttpClient httpClient = HttpClient.create();
        return WebClient
                .builder()
                .clientConnector(new ReactorClientHttpConnector(tracing.decorateHttpClient(httpClient)))
                .filter(logFilter()).filter(tokenFilter()).build();
    }

    private ExchangeFilterFunction logFilter() {
        return (clientRequest, next) -> {
            logger.info("External Request to {}", clientRequest.url());
            return next.exchange(clientRequest);
        };
    }

    private ExchangeFilterFunction tokenFilter() {
        return (clientRequest, next) -> {
            logger.info("Headers: {}", clientRequest.headers().get(HttpHeaders.AUTHORIZATION));
            Mono<String> authMono = Mono.deferContextual(ctx -> Mono.just(ctx.get("auth")));
            return authMono.flatMap(auth -> {
                ClientRequest modifyClientRequest = ClientRequest.from(clientRequest).headers(headers -> {
                    //headers.set(HttpHeaders.ORIGIN, r.getHeaders().getFirst(HttpHeaders.ORIGIN));
                    //headers.set(HttpHeaders.AUTHORIZATION, r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                    headers.set(HttpHeaders.AUTHORIZATION, auth);
                }).build();
                return next.exchange(modifyClientRequest);
            });
        };
    }
}
