package naughty.tuzamate.domain.stock.service.compare.async;

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

    @Value("${stock.collect.batch-size}")
    private int batchSize;

    public void saveKrxStocksInfo() {
        log.info("한국 주식 정보 저장/업데이트 시작");
        long start = System.currentTimeMillis();

        List<StockCode> stockCodeList = stockCodeRepository.findAll();

        // 비동기 API 호출
        List<CompletableFuture<Optional<KrxStockInfo>>> completableFutures = stockCodeList.stream()
                .map(stockCode -> asyncKrxStockFetcher.fetchStock(stockCode.getCode()))
                .toList();

        log.info("{}개의 한국 주식 정보 요청 시작", stockCodeList.size());


        // 모든 CompletableFuture가 완료될 때까지 기다린다
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join(); // join은 예외를 던지지 않는다
        log.info("모든 한국 주식 정보 요청 완료");

        List<KrxStockInfo> collectedStocks = new ArrayList<>(stockCodeList.size());

        for (CompletableFuture<Optional<KrxStockInfo>> completableFuture : completableFutures) {
            Optional<KrxStockInfo> stockInfo = completableFuture.join();
            if (stockInfo.isEmpty()) {
                continue;
            }

            collectedStocks.add(stockInfo.get());
        }

        asyncKrxPersistenceService.replaceAllKrxStocks(collectedStocks, batchSize);
        log.info("{} 개의 한국 주식 정보를 DB에 저장 완료", collectedStocks.size());

        long end = System.currentTimeMillis();
        log.info("한국 주식 정보 저장/업데이트 완료, 소요 시간: {} ms", (end - start));
    }
}
