package naughty.tuzamate.domain.stock.service.compare.support;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

public interface ApiRetryExecutor {
    <T> T executeBlocking(String apiName, Supplier<T> supplier);
    <T> Mono<T> executeReactive(String apiName, Mono<T> mono);
}
