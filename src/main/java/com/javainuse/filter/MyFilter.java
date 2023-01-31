package com.javainuse.filter;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class MyFilter extends AbstractGatewayFilterFactory<MyFilter.Config> {
    public MyFilter() {
        super(Config.class);
    }

    private boolean isAuthorizationValid(String authorizationHeader) {
        boolean isValid = true;

        // Logic for checking the value

        return isValid;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);

        return response.setComplete();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey("Authorization")) {
                return this.onError(exchange, "No Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authorizationHeader = request.getHeaders().get("Authorization").get(0);

            if (!this.isAuthorizationValid(authorizationHeader)) {
                return this.onError(exchange, "Invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate().
                    header("secret", RandomStringUtils.random(10)).
                    build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    public static class Config {
        // Put the configuration properties
    }
}
