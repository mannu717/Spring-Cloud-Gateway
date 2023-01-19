package com.javainuse;

import com.javainuse.dto.PersonDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class LoggingGlobalPreFilter implements GlobalFilter, Ordered {
    final Logger logger = LoggerFactory.getLogger(LoggingGlobalPreFilter.class);

    private final WebClient webClient;

    @Autowired
    public LoggingGlobalPreFilter(WebClient webClient) {
        this.webClient = webClient;
    }

    private List<HttpMessageReader<?>> getMessageReaders() {
        return HandlerStrategies.withDefaults().messageReaders();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("Global Pre Filter executed");

        ServerHttpRequest exchangeRequest = exchange.getRequest();
        Optional<String> profile_id = exchangeRequest.getHeaders().get("PROFILE_ID").stream().findFirst();
        String auth = exchangeRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return getPersonId(profile_id)
                .contextWrite(context -> context.put("auth", auth))
                .flatMap(personId -> {
                    logger.info("personId: {}", personId);

                    ServerHttpRequest request = exchangeRequest.mutate().header(HttpHeaders.AUTHORIZATION, "Bearer token").build();
                    return chain.filter(exchange.mutate().request(request).build());
                }).onErrorResume(throwable -> {
                    ServerHttpRequest request = exchangeRequest.mutate().header(HttpHeaders.AUTHORIZATION, "Bearer token").build();
                    return chain.filter(exchange.mutate().request(request).build());
                });
    }

    private Mono<String> getPersonId(Optional<String> profile_id) {
        if (profile_id.isPresent()) {
            String profileId = profile_id.get();
            return webClient.get().uri("http://localhost:8081/test/person/" + profileId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(PersonDataResponse.class)
                    .map(personDataResponse -> {
                        logger.info("personDataResponse: {}", personDataResponse);
                        String personId = personDataResponse.getPersonId();
                        return personId;
                    }).doOnError(throwable -> {
                        logger.error("Error: {}", throwable);
                    });
        }
        return Mono.just("");
    }

    @Override
    public int getOrder() {
        return -1;
    }
}