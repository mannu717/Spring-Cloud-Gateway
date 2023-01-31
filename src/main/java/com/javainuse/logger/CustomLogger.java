package com.javainuse.logger;

import org.slf4j.MDC;
import reactor.core.publisher.Signal;

import java.util.Optional;
import java.util.function.Consumer;

public class CustomLogger {

    public static <T> Consumer<Signal<T>> logOnNext(Consumer<T> logStatement) {
        return signal -> {
            if (!signal.isOnNext()) return;
            Optional<String> requestIdMdc = signal.getContextView().getOrEmpty("request_id");
            if (requestIdMdc.isPresent()) {
                String requestId = requestIdMdc.get();
                try (MDC.MDCCloseable cMdc = MDC.putCloseable("request_id", requestId)) {
                    logStatement.accept(signal.get());
                }
            } else {
                logStatement.accept(signal.get());
            }
        };
    }
}
