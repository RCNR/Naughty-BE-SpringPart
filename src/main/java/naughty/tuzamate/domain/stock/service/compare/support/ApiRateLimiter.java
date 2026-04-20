package naughty.tuzamate.domain.stock.service.compare.support;

import reactor.core.publisher.Mono;

public interface ApiRateLimiter {
    // API 호출 전에 호출 빈도 제한을 적용하는 메서드
    void acquireBlocking() throws InterruptedException;

    // API 호출 전에 호출 빈도 제한을 적용하는 메서드 (reactive 버전)
    Mono<Void> acquireReactive();
}
