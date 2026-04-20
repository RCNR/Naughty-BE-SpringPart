package naughty.tuzamate.domain.stock.service.compare.support;

import com.google.common.util.concurrent.RateLimiter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class BenchmarkRateLimiter implements ApiRateLimiter {

    private final RateLimiter guavaLimiter;

    // acquireReactive()용 기존 필드 유지
    private final long intervalNanos;
    private long nextAllowedTimeNanos;

    public BenchmarkRateLimiter(int permitsPerSecond) {
        int safe = Math.max(1, permitsPerSecond);
        this.guavaLimiter = RateLimiter.create(safe);
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / safe;
        this.nextAllowedTimeNanos = 0L;
    }

    /**
     * Guava RateLimiter는 lock을 쥐지 않는다
     * 각 스레드가 자신의 대기 시간 계산 후 sleep
     */
    @Override
    public void acquireBlocking() {
        guavaLimiter.acquire();
    }

    @Override
    public Mono<Void> acquireReactive() {
        long delayNanos;
        synchronized (this) { // 짧은 연산만 lock 안에서
            long now = System.nanoTime();
            delayNanos = Math.max(0, nextAllowedTimeNanos - now);
            nextAllowedTimeNanos = Math.max(now, nextAllowedTimeNanos) + intervalNanos;
        }
        if (delayNanos <= 0) {
            return Mono.empty();
        }
        return Mono.delay(Duration.ofNanos(delayNanos)).then(); // 비동기로 대기
    }
}
