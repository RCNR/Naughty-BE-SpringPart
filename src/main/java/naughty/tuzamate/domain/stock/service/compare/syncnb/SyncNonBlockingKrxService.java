package naughty.tuzamate.domain.stock.service.compare.syncnb;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncKrxPersistenceService;
import naughty.tuzamate.domain.stock.service.compare.reactive.KrxReactiveApiClient;
import naughty.tuzamate.domain.stock.service.compare.support.BenchmarkConfig;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncNonBlockingKrxService {

    private final StockCodeRepository stockCodeRepository;
    private final KrxReactiveApiClient reactiveApiClient;
    private final FilterStrategy filterStrategy;
    private final AsyncKrxPersistenceService persistenceService;
    private final MeterRegistry meterRegistry;
    private final BenchmarkConfig.BenchmarkProperties benchmarkProperties;

    private static final String MODEL = "sync-nb";

    @Value("${stock.collect.batch-size:200}")
    private int batchSize;

    public void saveKrxStocksInfo() {
        log.info("[{}] 한국 주식 정보 저장 시작", MODEL);
        Timer.Sample totalSample = Timer.start(meterRegistry);

        List<StockCode> stockCodeList = stockCodeRepository.findAll();
        int concurrencyLimit = benchmarkProperties.concurrencyLimit();

        Timer.Sample outboundSample = Timer.start(meterRegistry);

        List<KrxStockInfo> collectedStocks = new ArrayList<>();

        // Batch-and-wait: partition into batches, dispatch all in batch via toFuture(), wait all
        for (int i = 0; i < stockCodeList.size(); i += concurrencyLimit) {
            List<StockCode> batch = stockCodeList.subList(i, Math.min(i + concurrencyLimit, stockCodeList.size()));

            List<CompletableFuture<Optional<KrxStockInfo>>> futures = batch.stream()
                    .map(sc -> fetchSingleStock(sc.getCode()).toFuture())
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<Optional<KrxStockInfo>> future : futures) {
                future.join().ifPresent(collectedStocks::add);
            }

        }

        outboundSample.stop(Timer.builder("krx.outbound.total")
                .tag("model", MODEL)
                .register(meterRegistry));
        log.info("[{}] 모든 API 호출 완료, {}건 수집", MODEL, collectedStocks.size());

        Timer.Sample inboundSample = Timer.start(meterRegistry);
        persistenceService.replaceAllKrxStocks(collectedStocks, batchSize);
        inboundSample.stop(Timer.builder("krx.inbound.db")
                .tag("model", MODEL)
                .register(meterRegistry));

        totalSample.stop(Timer.builder("krx.pipeline.total")
                .tag("model", MODEL)
                .register(meterRegistry));
        log.info("[{}] 한국 주식 정보 저장 완료", MODEL);
    }

    private Mono<Optional<KrxStockInfo>> fetchSingleStock(String stockCode) {
        Mono<KrxDto.InquireDto> inquireMono = Mono.defer(() -> {
            Timer.Sample s = Timer.start(meterRegistry);
            return reactiveApiClient.getCurInquireInfo(stockCode)
                    .doOnSuccess(dto -> s.stop(Timer.builder("krx.api.outbound")
                            .tag("model", MODEL).tag("api", "inquire")
                            .register(meterRegistry)));
        });

        Mono<KrxDto.FinancialDto> financialMono = Mono.defer(() -> {
            Timer.Sample s = Timer.start(meterRegistry);
            return reactiveApiClient.getCurFinancialInfo(stockCode)
                    .doOnSuccess(dto -> s.stop(Timer.builder("krx.api.outbound")
                            .tag("model", MODEL).tag("api", "financial")
                            .register(meterRegistry)));
        });

        return Mono.zip(inquireMono, financialMono)
                .flatMap(tuple -> {
                    KrxDto.InquireDto inquire = tuple.getT1();
                    KrxDto.FinancialDto financial = tuple.getT2();

                    if (filterStrategy.shouldSkipKrx(inquire, financial)) {
                        return Mono.just(Optional.<KrxStockInfo>empty());
                    }

                    return Mono.defer(() -> {
                        Timer.Sample s = Timer.start(meterRegistry);
                        return reactiveApiClient.getStockInfo(stockCode, "300")
                                .doOnSuccess(dto -> s.stop(Timer.builder("krx.api.outbound")
                                        .tag("model", MODEL).tag("api", "stock-info")
                                        .register(meterRegistry)))
                                .map(stockInfo -> {
                                    KrxDto.KrxStockInfoDto dto = new KrxDto.KrxStockInfoDto();
                                    log.info("Saved stocks is : {}", stockCode);
                                    return Optional.of(dto.toEntity(inquire, financial, stockInfo));
                                });
                    });
                })
                .onErrorResume(e -> {
                    log.error("[{}] Error fetching stock {}: {}", MODEL, stockCode, e.getMessage());
                    return Mono.just(Optional.empty());
                });
    }
}
