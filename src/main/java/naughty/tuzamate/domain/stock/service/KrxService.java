package naughty.tuzamate.domain.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.KrxStockInfoRepository;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import naughty.tuzamate.domain.stock.service.support.StockRequestRateLimiter;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KrxService {

    private final StockCodeRepository stockCodeRepository;
    private final KrxInquireService krxInquireService;
    private final KrxFinancialService krxFinancialService;
    private final KrxStockInfoRepository krxStockInfoRepository;
    private final StockInfoService stockInfoService;
    private final FilterStrategy filterStrategy;
    private final StockRequestRateLimiter stockRequestRateLimiter;

    @Value("${stock.collect.batch-size:200}")
    private int batchSize;

    public void saveKrxStocksInfo() {

        List<StockCode> stockCodeList = stockCodeRepository.findAll();
        KrxCollectionMetrics metrics = new KrxCollectionMetrics(stockCodeList.size());

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("주식 저장 시작");

        krxStockInfoRepository.deleteAllInBatch();
        List<KrxStockInfo> batchBuffer = new ArrayList<>(batchSize);

        for (StockCode stockCode : stockCodeList) {
            try {
                // sleep 대신 동기 RateLimiter로 요청 간격 제어
                stockRequestRateLimiter.acquire();

                long inquireStart = System.nanoTime();
                // 주식 코드를 이용해 현재가, PER, PBR, 업종 한글 종목명 조회
                KrxDto.InquireDto currentPerPbrOutputDto = krxInquireService.getCurInquireInfo(stockCode.getCode());
                metrics.addInquireApiNanos(System.nanoTime() - inquireStart);

                long financialStart = System.nanoTime();
                // 주식 코드를 이용해 EPS 값 조회
                KrxDto.FinancialDto currentFinanceOutputDto = krxFinancialService.getCurFinancialInfo(stockCode.getCode());
                metrics.addFinancialApiNanos(System.nanoTime() - financialStart);

                long stockInfoStart = System.nanoTime();
                StockInfoDto.InfoDto currentKrxStockInfoDto = stockInfoService.getStockInfo(stockCode.getCode(), "300");
                metrics.addStockInfoApiNanos(System.nanoTime() - stockInfoStart);


                if (filterStrategy.shouldSkipKrx(currentPerPbrOutputDto, currentFinanceOutputDto)) {
                    metrics.incrementSkippedCount();
                    log.info("PER or PBR or EPS is zero: {}", stockCode.getCode());
                    continue;
                }

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

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 현재 스레드 인터럽트 상태 복구
                log.info("Thread Interrupted : {}", e.getMessage());
                break;
            } catch (Exception e) {
                metrics.incrementFailedCount();
                log.info("Error stock code is {} : {} and pass!", stockCode.getCode(), e.getMessage());
            }
        }
        flushBatch(batchBuffer, metrics);

        stopWatch.stop();
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

        private int getTotalStocks() {
            return totalStocks;
        }

        private int getSavedCount() {
            return savedCount;
        }

        private int getSkippedCount() {
            return skippedCount;
        }

        private int getFailedCount() {
            return failedCount;
        }

        private int getBatchFlushCount() {
            return batchFlushCount;
        }

        private long getInquireApiNanos() {
            return inquireApiNanos;
        }

        private long getFinancialApiNanos() {
            return financialApiNanos;
        }

        private long getStockInfoApiNanos() {
            return stockInfoApiNanos;
        }

        private long getBatchSaveNanos() {
            return batchSaveNanos;
        }

        private long getTotalApiNanos() {
            return inquireApiNanos + financialApiNanos + stockInfoApiNanos;
        }
    }
}
