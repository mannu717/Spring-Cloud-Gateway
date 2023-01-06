package com.javainuse;

import org.bouncycastle.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EncryptDecryptFilter2 implements GlobalFilter, Ordered {

    final Logger logger = LoggerFactory.getLogger(LoggingGlobalPreFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("Applying encrypt-decrypt filter");
        return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
            ServerHttpRequest mutatedHttpRequest = getServerHttpRequest(exchange, dataBuffer);
            return chain.filter(exchange.mutate().request(mutatedHttpRequest).build());
        });
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
                return httpHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(body).map(s -> new DefaultDataBufferFactory().wrap(decryptedBodyBytes));
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

    @Override
    public int getOrder() {
        return 0;
    }
}
