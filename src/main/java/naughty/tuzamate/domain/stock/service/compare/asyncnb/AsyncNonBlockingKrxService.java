package naughty.tuzamate.domain.stock.service.compare.asyncnb;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncKrxPersistenceService;
import naughty.tuzamate.domain.stock.service.compare.reactive.KrxReactiveApiClient;
import naughty.tuzamate.domain.stock.service.compare.support.BenchmarkConfig;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNonBlockingKrxService {

    private final StockCodeRepository stockCodeRepository;
    private final KrxReactiveApiClient reactiveApiClient;
    private final FilterStrategy filterStrategy;
    private final AsyncKrxPersistenceService persistenceService;
    private final MeterRegistry meterRegistry;
    private final BenchmarkConfig.BenchmarkProperties benchmarkProperties;

    private static final String MODEL = "async-nb";

    @Value("${stock.collect.batch-size:200}")
    private int batchSize;

    public Mono<Void> saveKrxStocksInfo() {
        log.info("[{}] 한국 주식 정보 저장 시작", MODEL);
        Timer.Sample totalSample = Timer.start(meterRegistry);
        Timer.Sample outboundSample = Timer.start(meterRegistry);

        int concurrencyLimit = benchmarkProperties.concurrencyLimit();

        return Flux.defer(() -> Flux.fromIterable(stockCodeRepository.findAll()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stockCode -> fetchSingleStock(stockCode.getCode()), concurrencyLimit)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collectList()
                .doOnNext(list -> {
                    outboundSample.stop(Timer.builder("krx.outbound.total")
                            .tag("model", MODEL)
                            .register(meterRegistry));
                    log.info("[{}] 모든 API 호출 완료, {}건 수집", MODEL, list.size());
                })
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(list -> {
                    Timer.Sample inboundSample = Timer.start(meterRegistry);
                    persistenceService.replaceAllKrxStocks(list, batchSize);
                    inboundSample.stop(Timer.builder("krx.inbound.db")
                            .tag("model", MODEL)
                            .register(meterRegistry));
                })
                .doOnSuccess(list -> {
                    totalSample.stop(Timer.builder("krx.pipeline.total")
                            .tag("model", MODEL)
                            .register(meterRegistry));
                    log.info("[{}] 한국 주식 정보 저장 완료", MODEL);
                })
                .then();
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
