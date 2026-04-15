package naughty.tuzamate.domain.stock.service.compare.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public class BenchmarkRetryExecutor implements ApiRetryExecutor {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final MeterRegistry meterRegistry;
    private final String model;

    // model은 "blocking" 또는 "reactive"로, 메트릭 태그에 사용하여 구분
    public BenchmarkRetryExecutor(int maxAttempts, long initialBackoffMs,
                                  MeterRegistry meterRegistry, String model) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.meterRegistry = meterRegistry;
        this.model = model;
    }

    @Override
    public <T> T executeBlocking(String apiName, Supplier<T> supplier) {
        long backoffMs = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (ResourceAccessException e) {
                incrementErrorCounter("timeout");
                if (attempt == maxAttempts) throw e;
                log.warn("[{}] timeout/network error, retry {}/{}", apiName, attempt, maxAttempts);
                sleepBackoff(backoffMs);
                backoffMs *= 2;
            } catch (RestClientResponseException e) {
                String errorType = categorizeHttpStatus(e.getStatusCode());
                incrementErrorCounter(errorType);
                if (!isRetryableStatus(e.getStatusCode()) || attempt == maxAttempts) throw e;
                log.warn("[{}] status={}, retry {}/{}", apiName, e.getStatusCode().value(), attempt, maxAttempts);
                sleepBackoff(backoffMs);
                backoffMs *= 2;
            }
        }
        throw new IllegalStateException("Retry failed unexpectedly");
    }

    @Override
    public <T> Mono<T> executeReactive(String apiName, Mono<T> mono) {
        return mono.retryWhen(
                Retry.backoff(maxAttempts - 1, Duration.ofMillis(initialBackoffMs))
                        .filter(this::isRetryableReactive)
                        .doBeforeRetry(signal -> {
                            String errorType = categorizeReactiveError(signal.failure());
                            incrementErrorCounter(errorType);
                            log.warn("[{}] reactive retry {}/{}: {}",
                                    apiName, signal.totalRetries() + 1, maxAttempts - 1,
                                    signal.failure().getMessage());
                        })
        ).doOnError(e -> {
            String errorType = categorizeReactiveError(e);
            incrementErrorCounter(errorType);
        });
    }

    private boolean isRetryableReactive(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof ConnectException
                || throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.io.IOException;
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }

    private String categorizeHttpStatus(HttpStatusCode statusCode) {
        if (statusCode.value() == 429) return "rate-limited";
        if (statusCode.is5xxServerError()) return "server-error";
        return "network";
    }

    private String categorizeReactiveError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return categorizeHttpStatus(ex.getStatusCode());
        }
        if (throwable instanceof ConnectException) return "timeout";
        if (throwable instanceof java.net.SocketTimeoutException) return "timeout";
        return "network";
    }

    private void incrementErrorCounter(String errorType) {
        Counter.builder("krx.api.errors")
                .tag("model", model)
                .tag("error-type", errorType)
                .register(meterRegistry)
                .increment();
    }

    private void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }
}
