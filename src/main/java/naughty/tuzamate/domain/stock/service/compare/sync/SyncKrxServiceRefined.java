package naughty.tuzamate.domain.stock.service.compare.sync;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.KrxStockInfoRepository;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import naughty.tuzamate.domain.stock.service.compare.blocking.KrxBlockingApiClient;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
// 동기적 API 호출 + 배치 저장 전략을 적용한 KRX 수집 서비스
public class SyncKrxServiceRefined {

    private final StockCodeRepository stockCodeRepository;
    private final KrxBlockingApiClient blockingApiClient;
    private final KrxStockInfoRepository krxStockInfoRepository;
    private final FilterStrategy filterStrategy;
    private final MeterRegistry meterRegistry;

    private static final String MODEL = "sync-blocking";

    @Value("${stock.collect.batch-size}")
    private int batchSize;

    public void saveKrxStocksInfo() {

        List<StockCode> stockCodeList = stockCodeRepository.findAll();
        KrxCollectionMetrics metrics = new KrxCollectionMetrics(stockCodeList.size());

        Timer.Sample totalSample = Timer.start(meterRegistry);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("주식 저장 시작");

        // Outbound 전체 구간 측정: 동기 API 호출 + 처리
        Timer.Sample outboundSample = Timer.start(meterRegistry);

        krxStockInfoRepository.deleteAllInBatch();
        List<KrxStockInfo> batchBuffer = new ArrayList<>(batchSize);

        for (StockCode stockCode : stockCodeList) {
            try {
                Timer.Sample inquireSample = Timer.start(meterRegistry);
                long inquireStart = System.nanoTime();
                KrxDto.InquireDto currentPerPbrOutputDto = blockingApiClient.getCurInquireInfo(stockCode.getCode());
                metrics.addInquireApiNanos(System.nanoTime() - inquireStart);
                inquireSample.stop(Timer.builder("krx.api.outbound")
                        .tag("model", MODEL).tag("api", "inquire")
                        .register(meterRegistry));

                Timer.Sample financialSample = Timer.start(meterRegistry);
                long financialStart = System.nanoTime();
                KrxDto.FinancialDto currentFinanceOutputDto = blockingApiClient.getCurFinancialInfo(stockCode.getCode());
                metrics.addFinancialApiNanos(System.nanoTime() - financialStart);
                financialSample.stop(Timer.builder("krx.api.outbound")
                        .tag("model", MODEL).tag("api", "financial")
                        .register(meterRegistry));

                if (filterStrategy.shouldSkipKrx(currentPerPbrOutputDto, currentFinanceOutputDto)) {
                    metrics.incrementSkippedCount();
                    log.info("PER or PBR or EPS is zero: {}", stockCode.getCode());
                    continue;
                }

                Timer.Sample stockInfoSample = Timer.start(meterRegistry);
                long stockInfoStart = System.nanoTime();
                StockInfoDto.InfoDto currentKrxStockInfoDto = blockingApiClient.getStockInfo(stockCode.getCode(), "300");
                metrics.addStockInfoApiNanos(System.nanoTime() - stockInfoStart);
                stockInfoSample.stop(Timer.builder("krx.api.outbound")
                        .tag("model", MODEL).tag("api", "stock-info")
                        .register(meterRegistry));


                KrxDto.KrxStockInfoDto stockInfoDto = new KrxDto.KrxStockInfoDto();

                KrxStockInfo stockInfo = stockInfoDto.toEntity(
                        currentPerPbrOutputDto,
                        currentFinanceOutputDto,
                        currentKrxStockInfoDto
                );

                batchBuffer.add(stockInfo);
                flushBatchIfNeeded(batchBuffer, metrics);
                metrics.incrementSavedCount();

                log.info("Saved stocks is : {}", stockCode.getCode());

            } catch (Exception e) {
                metrics.incrementFailedCount();
                log.info("Error stock code is {} : {} and pass!", stockCode.getCode(), e.getMessage());
            }
        }

        outboundSample.stop(Timer.builder("krx.outbound.total")
                .tag("model", MODEL)
                .register(meterRegistry));

        // Inbound 구간 측정: DB 저장
        Timer.Sample inboundSample = Timer.start(meterRegistry);
        flushBatch(batchBuffer, metrics);
        inboundSample.stop(Timer.builder("krx.inbound.db")
                .tag("model", MODEL)
                .register(meterRegistry));

        totalSample.stop(Timer.builder("krx.pipeline.total")
                .tag("model", MODEL)
                .register(meterRegistry));

        stopWatch.stop();

        // 수집 성능 요약 로그 출력
        logCollectionMetrics(stopWatch, metrics);
        log.info("주식 저장 종료. 총 소요 시간: {} seconds", stopWatch.getTotalTimeSeconds());
    }

    private void flushBatchIfNeeded(List<KrxStockInfo> batchBuffer, KrxCollectionMetrics metrics) {
        if (batchBuffer.size() >= batchSize) {
            flushBatch(batchBuffer, metrics);
        }
    }

    private void flushBatch(List<KrxStockInfo> batchBuffer, KrxCollectionMetrics metrics) {
        if (batchBuffer.isEmpty()) {
            return;
        }
        // 단건 save 반복 대신 배치 저장
        long batchSaveStart = System.nanoTime();
        krxStockInfoRepository.saveAll(batchBuffer);
        metrics.addBatchSaveNanos(System.nanoTime() - batchSaveStart);
        metrics.incrementBatchFlushCount();
        batchBuffer.clear();
    }

    private void logCollectionMetrics(StopWatch stopWatch, KrxCollectionMetrics metrics) {
        long totalNanos = stopWatch.getTotalTimeNanos();
        long apiNanos = metrics.getTotalApiNanos();
        long batchSaveNanos = metrics.getBatchSaveNanos();

        log.info(
                "KRX 수집 성능 요약 totalStocks={}, saved={}, skipped={}, failed={}, batchFlushes={}, totalMs={}, apiTotalMs={}, inquireMs={}, financialMs={}, stockInfoMs={}, batchSaveMs={}, nonApiNonDbMs={}",
                metrics.getTotalStocks(),
                metrics.getSavedCount(),
                metrics.getSkippedCount(),
                metrics.getFailedCount(),
                metrics.getBatchFlushCount(),
                nanosToMillis(totalNanos),
                nanosToMillis(apiNanos),
                nanosToMillis(metrics.getInquireApiNanos()),
                nanosToMillis(metrics.getFinancialApiNanos()),
                nanosToMillis(metrics.getStockInfoApiNanos()),
                nanosToMillis(batchSaveNanos),
                nanosToMillis(Math.max(0L, totalNanos - apiNanos - batchSaveNanos))
        );
    }

    private long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    @Getter
    private static class KrxCollectionMetrics {
        private final int totalStocks;
        private int savedCount;
        private int skippedCount;
        private int failedCount;
        private int batchFlushCount;
        private long inquireApiNanos;
        private long financialApiNanos;
        private long stockInfoApiNanos;
        private long batchSaveNanos;

        private KrxCollectionMetrics(int totalStocks) {
            this.totalStocks = totalStocks;
        }

        private void incrementSavedCount() {
            savedCount++;
        }

        private void incrementSkippedCount() {
            skippedCount++;
        }

        private void incrementFailedCount() {
            failedCount++;
        }

        private void incrementBatchFlushCount() {
            batchFlushCount++;
        }

        private void addInquireApiNanos(long nanos) {
            inquireApiNanos += nanos;
        }

        private void addFinancialApiNanos(long nanos) {
            financialApiNanos += nanos;
        }

        private void addStockInfoApiNanos(long nanos) {
            stockInfoApiNanos += nanos;
        }

        private void addBatchSaveNanos(long nanos) {
            batchSaveNanos += nanos;
        }

        private long getTotalApiNanos() {
            return inquireApiNanos + financialApiNanos + stockInfoApiNanos;
        }
    }
}
