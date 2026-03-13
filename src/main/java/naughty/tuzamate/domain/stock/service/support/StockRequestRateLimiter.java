package naughty.tuzamate.domain.stock.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class StockRequestRateLimiter {

    private final long intervalNanos;
    private long nextAllowedTimeNanos;

    public StockRequestRateLimiter(@Value("${stock.api.rate-limit-per-second}") int permitsPerSecond) {

        int safePermitsPerSecond = Math.max(1, permitsPerSecond);

        // 요청 간 최소 간격
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / safePermitsPerSecond;

        // 다음 허용되는 시각
        this.nextAllowedTimeNanos = 0L;

    }

    public synchronized void acquire() throws InterruptedException {
        long now = System.nanoTime();

        if (now < nextAllowedTimeNanos) {
            // 아직 다음 허용 시각이 되지 않았으므로 대기
            long waitTimeNanos = nextAllowedTimeNanos - now;
            TimeUnit.NANOSECONDS.sleep(waitTimeNanos);
        }

        // 다음 허용 시각 업데이트
        nextAllowedTimeNanos = Math.max(now, nextAllowedTimeNanos) + intervalNanos;
    }

}
