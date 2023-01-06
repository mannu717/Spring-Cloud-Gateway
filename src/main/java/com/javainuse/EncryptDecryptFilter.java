package com.javainuse;

import org.bouncycastle.util.Strings;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

//@Component
public class EncryptDecryptFilter extends AbstractGatewayFilterFactory<EncryptDecryptFilter.Config> {

    @Override
    public GatewayFilter apply(Config config) {
        System.out.println("Here in filter");
        return new OrderedGatewayFilter((exchange, chain) -> {
            System.out.println("Applying encrypt-decrypt filter");
            return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
                ServerHttpRequest mutatedHttpRequest = getServerHttpRequest(exchange, dataBuffer);
                return chain.filter(exchange.mutate().request(mutatedHttpRequest).build());
            });
        }, 0);
    }

    private ServerHttpRequest getServerHttpRequest(ServerWebExchange exchange, DataBuffer dataBuffer) {
        DataBufferUtils.retain(dataBuffer);
        Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
        String body = toRaw(cachedFlux);
        String decryptedBody = EncryptDecryptHelper.decrypt(body);
        byte[] decryptedBodyBytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.putAll(exchange.getRequest().getHeaders());
                if (decryptedBodyBytes.length > 0) {
                    httpHeaders.setContentLength(decryptedBodyBytes.length);
                }
                httpHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
                httpHeaders.set(HttpHeaders.PROXY_AUTHORIZATION, "Bearer token");
                return httpHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(body).
                        map(s -> {
                            return new DefaultDataBufferFactory().wrap(decryptedBodyBytes);
                        });
            }
        };
    }

    private String toRaw(Flux<DataBuffer> body) {
        AtomicReference<String> rawRef = new AtomicReference<>();
        body.subscribe(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            rawRef.set(Strings.fromUTF8ByteArray(bytes));
        });
        return rawRef.get();
    }

    public static class Config {
        public Config() {
        }
    }
}
