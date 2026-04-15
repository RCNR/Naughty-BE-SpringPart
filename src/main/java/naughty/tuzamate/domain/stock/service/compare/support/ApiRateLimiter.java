package naughty.tuzamate.domain.stock.service.compare.support;

import reactor.core.publisher.Mono;

public interface ApiRateLimiter {
    void acquireBlocking() throws InterruptedException;
    Mono<Void> acquireReactive();
}
