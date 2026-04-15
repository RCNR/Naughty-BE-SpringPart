package naughty.tuzamate.domain.stock.service.compare.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BenchmarkConfig {

    @Value("${stock.api.rate-limit-per-second:5}")
    private int rateLimitPerSecond;

    @Value("${stock.api.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${stock.api.retry.initial-backoff-ms:200}")
    private long initialBackoffMs;

    @Value("${stock.benchmark.concurrency-limit:8}")
    private int concurrencyLimit;

    @Value("${stock.benchmark.warmup-count:3}")
    private int warmupCount;

    @Bean
    public ApiRateLimiter benchmarkRateLimiter() {
        return new BenchmarkRateLimiter(rateLimitPerSecond);
    }

    // blocking, reactive 각각 별도의 ApiRetryExecutor 빈 등록 -> 메트릭 태그로 구분하여 재시도 횟수 집계
    @Bean("blockingRetryExecutor")
    public ApiRetryExecutor blockingRetryExecutor(MeterRegistry meterRegistry) {
        return new BenchmarkRetryExecutor(maxAttempts, initialBackoffMs, meterRegistry, "blocking");
    }

    @Bean("reactiveRetryExecutor")
    public ApiRetryExecutor reactiveRetryExecutor(MeterRegistry meterRegistry) {
        return new BenchmarkRetryExecutor(maxAttempts, initialBackoffMs, meterRegistry, "reactive");
    }

    // 벤치마크 실행 시 동시성 수준과 워밍업 횟수를 설정하는 빈 -> 벤치마크 서비스에서 주입받아 사용
    @Bean
    public BenchmarkProperties benchmarkProperties() {
        return new BenchmarkProperties(concurrencyLimit, warmupCount);
    }

    public record BenchmarkProperties(int concurrencyLimit, int warmupCount) {}
}
