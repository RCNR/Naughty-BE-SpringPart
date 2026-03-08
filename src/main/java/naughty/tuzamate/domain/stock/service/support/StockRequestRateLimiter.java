package naughty.tuzamate.domain.stock.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class StockRequestRateLimiter {

    private final long intervalNanos;
    private long nextAllowedTimeNanos;

    public StockRequestRateLimiter(@Value("${stock.api.rate-limit-per-second:10}") int permitsPerSecond) {
        int safePermitsPerSecond = Math.max(1, permitsPerSecond);

        // 요청 간 최소 간격
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / safePermitsPerSecond;

        // 다음 요청 허용되는 시각
        this.nextAllowedTimeNanos = 0L;
    }

    // 동기 방식에서 호출 간격을 일정하게 보장
    public synchronized void acquire() throws InterruptedException {
        long now = System.nanoTime();
        if (now < nextAllowedTimeNanos) {
            TimeUnit.NANOSECONDS.sleep(nextAllowedTimeNanos - now);
            now = System.nanoTime();
        }
        nextAllowedTimeNanos = Math.max(now, nextAllowedTimeNanos) + intervalNanos;
    }
}
