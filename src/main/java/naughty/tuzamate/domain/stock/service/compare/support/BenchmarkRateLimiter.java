package naughty.tuzamate.domain.stock.service.compare.support;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class BenchmarkRateLimiter implements ApiRateLimiter {

    private final long intervalNanos;
    private long nextAllowedTimeNanos;

    // 초당 허용할 최대 요청 수를 받아서, 요청 1건당 최소 간격을 계산하여 저장
    public BenchmarkRateLimiter(int permitsPerSecond) {
        int safe = Math.max(1, permitsPerSecond);
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / safe;
        this.nextAllowedTimeNanos = 0L;
    }

    @Override
    // 여러 스레드에서 동시에 호출되면 nextAllowedTimeNanos 꼬이게 업데이트 될 수 있음 -> synchronized로 동기화
    public synchronized void acquireBlocking() throws InterruptedException {
        long now = System.nanoTime();
        if (now < nextAllowedTimeNanos) {
            long waitNanos = nextAllowedTimeNanos - now;
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
        nextAllowedTimeNanos = Math.max(now, nextAllowedTimeNanos) + intervalNanos;
    }

    @Override
    public Mono<Void> acquireReactive() {
        long delayNanos;
        synchronized (this) {
            long now = System.nanoTime();
            delayNanos = Math.max(0, nextAllowedTimeNanos - now);
            nextAllowedTimeNanos = Math.max(now, nextAllowedTimeNanos) + intervalNanos;
        }
        if (delayNanos <= 0) {
            return Mono.empty();
        }
        return Mono.delay(Duration.ofNanos(delayNanos)).then();
    }
}
