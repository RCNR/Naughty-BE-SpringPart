package naughty.tuzamate.domain.stock.service.compare.async;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncKrxService {

    private final StockCodeRepository stockCodeRepository;
    private final AsyncKrxStockFetcher asyncKrxStockFetcher;
    private final AsyncKrxPersistenceService asyncKrxPersistenceService;
    private final MeterRegistry meterRegistry;

    @Value("${stock.collect.batch-size}")
    private int batchSize;

    public void saveKrxStocksInfo() {
        log.info("한국 주식 정보 저장/업데이트 시작");
        Timer.Sample totalSample = Timer.start(meterRegistry);

        List<StockCode> stockCodeList = stockCodeRepository.findAll();

        // Outbound 전체 구간 측정: 비동기 API 호출 + 대기
        Timer.Sample outboundSample = Timer.start(meterRegistry);

        List<CompletableFuture<Optional<KrxStockInfo>>> completableFutures = stockCodeList.stream()
                .map(stockCode -> asyncKrxStockFetcher.fetchStock(stockCode.getCode()))
                .toList();

        log.info("{}개의 한국 주식 정보 요청 시작", stockCodeList.size());

        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        outboundSample.stop(Timer.builder("krx.outbound.total")
                .description("전체 외부 API 호출 소요 시간")
                .register(meterRegistry));
        log.info("모든 한국 주식 정보 요청 완료");

        List<KrxStockInfo> collectedStocks = new ArrayList<>(stockCodeList.size());

        for (CompletableFuture<Optional<KrxStockInfo>> completableFuture : completableFutures) {
            Optional<KrxStockInfo> stockInfo = completableFuture.join();
            if (stockInfo.isEmpty()) {
                continue;
            }

            collectedStocks.add(stockInfo.get());
        }

        // Inbound 구간 측정: DB 저장
        Timer.Sample inboundSample = Timer.start(meterRegistry);
        asyncKrxPersistenceService.replaceAllKrxStocks(collectedStocks, batchSize);
        inboundSample.stop(Timer.builder("krx.inbound.db")
                .description("DB 저장 소요 시간")
                .register(meterRegistry));
        log.info("{} 개의 한국 주식 정보를 DB에 저장 완료", collectedStocks.size());

        totalSample.stop(Timer.builder("krx.pipeline.total")
                .description("전체 파이프라인 소요 시간 (outbound + inbound)")
                .register(meterRegistry));
        log.info("한국 주식 정보 저장/업데이트 완료");
    }
}
