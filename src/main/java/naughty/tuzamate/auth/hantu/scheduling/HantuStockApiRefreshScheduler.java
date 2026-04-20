package naughty.tuzamate.auth.hantu.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncKrxService;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncNasdaqService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
@ConditionalOnProperty(name = "hantu.stock.schedule.enabled", havingValue = "true")
public class HantuStockApiRefreshScheduler {

    private final AsyncKrxService krxService;
    private final AsyncNasdaqService asyncNasdaqService;
    private final MeterRegistry meterRegistry;
    private final Executor krxTaskExecutor;
    private final Executor nasdaqTaskExecutor;

    public HantuStockApiRefreshScheduler(
            AsyncKrxService krxService,
            AsyncNasdaqService asyncNasdaqService,
            MeterRegistry meterRegistry,
            @Qualifier("krxTaskExecutor") Executor krxTaskExecutor,
            @Qualifier("nasdaqTaskExecutor") Executor nasdaqTaskExecutor) {
        this.krxService = krxService;
        this.asyncNasdaqService = asyncNasdaqService;
        this.meterRegistry = meterRegistry;
        this.krxTaskExecutor = krxTaskExecutor;
        this.nasdaqTaskExecutor = nasdaqTaskExecutor;
    }

    @Scheduled(cron = "0 39 13 * * *")
    public void refreshStockData() {
        log.info("Hantu Stock API 데이터 갱신 시작 (병렬)");
        Timer.Sample totalSample = Timer.start(meterRegistry);

        CompletableFuture<Void> krxFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("Hantu Krx API 데이터 갱신 시작");
                krxService.saveKrxStocksInfo();
                log.info("Hantu Krx API 데이터 갱신 완료");
            } catch (Exception e) {
                log.error("KRX 데이터 갱신 실패", e);
            }
        }, krxTaskExecutor);

        CompletableFuture<Void> nasdaqFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("Hantu Nasdaq API 데이터 갱신 시작");
                asyncNasdaqService.saveNasdaqStocksInfo();
                log.info("Hantu Nasdaq API 데이터 갱신 완료");
            } catch (Exception e) {
                log.error("NASDAQ 데이터 갱신 실패", e);
            }
        }, nasdaqTaskExecutor);

        try {
            CompletableFuture.allOf(krxFuture, nasdaqFuture)
                    .get(30, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            log.error("Stock API 갱신 타임아웃 (30분 초과)", e);
        } catch (Exception e) {
            log.error("Stock API 갱신 대기 중 예외", e);
        }

        totalSample.stop(Timer.builder("stock.parallel.total")
                .register(meterRegistry));
        log.info("Hantu Stock API 데이터 갱신 완료 (병렬)");
    }
}
