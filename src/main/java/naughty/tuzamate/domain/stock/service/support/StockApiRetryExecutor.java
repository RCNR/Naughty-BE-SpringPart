package naughty.tuzamate.domain.stock.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

@Component
@Slf4j
public class StockApiRetryExecutor {

    @Value("${stock.api.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${stock.api.retry.initial-backoff-ms:200}")
    private long initialBackoffMs;

    // 일시적 오류(429/5xx/timeout)만 제한적으로 재시도
    public <T> T execute(String apiName, Supplier<T> supplier) {
        long backoffMs = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (ResourceAccessException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                log.warn("[{}] timeout/network error, retry {}/{}", apiName, attempt, maxAttempts);
                waitBackoff(backoffMs);
                backoffMs *= 2;
            } catch (RestClientResponseException e) {
                if (!isRetryable(e.getStatusCode()) || attempt == maxAttempts) {
                    throw e;
                }
                log.warn("[{}] status={}, retry {}/{}", apiName, e.getStatusCode().value(), attempt, maxAttempts);
                waitBackoff(backoffMs);
                backoffMs *= 2;
            }
        }
        throw new IllegalStateException("Retry failed unexpectedly");
    }

    private boolean isRetryable(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }

    private void waitBackoff(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }
}
