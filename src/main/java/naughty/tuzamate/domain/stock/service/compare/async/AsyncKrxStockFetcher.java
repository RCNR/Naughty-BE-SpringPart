package naughty.tuzamate.domain.stock.service.compare.async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.service.compare.blocking.KrxBlockingApiClient;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncKrxStockFetcher {

    private final KrxBlockingApiClient blockingApiClient;
    private final FilterStrategy filterStrategy;
    private final MeterRegistry meterRegistry;

    /**
     * 3개의 API호출과 결과를 비동기 메소드로 묶는다.
     * StockCode를 받아 KrxStockInfo 엔티티를 CompleteableFuture로 반환한다.
     */
    @Async("taskExecutor") // 별도 쓰레드에서의 비동기 실행을 위한 어노테이션
    public CompletableFuture<Optional<KrxStockInfo>> fetchStock(String stockCode) {

        try {
            Timer.Sample inquireSample = Timer.start(meterRegistry);
            KrxDto.InquireDto currentPerPbrOutputDto = blockingApiClient.getCurInquireInfo(stockCode);
            inquireSample.stop(Timer.builder("krx.api.outbound")
                    .tag("model", "async-blocking").tag("api", "inquire")
                    .register(meterRegistry));

            Timer.Sample financialSample = Timer.start(meterRegistry);
            KrxDto.FinancialDto currentFinanceOutputDto = blockingApiClient.getCurFinancialInfo(stockCode);
            financialSample.stop(Timer.builder("krx.api.outbound")
                    .tag("model", "async-blocking").tag("api", "financial")
                    .register(meterRegistry));

            if (filterStrategy.shouldSkipKrx(currentPerPbrOutputDto, currentFinanceOutputDto)) {
                log.info("PER or PBR or EPS is zero: {}", stockCode);
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Timer.Sample stockInfoSample = Timer.start(meterRegistry);
            StockInfoDto.InfoDto currentKrxStockInfoDto = blockingApiClient.getStockInfo(stockCode, "300");
            stockInfoSample.stop(Timer.builder("krx.api.outbound")
                    .tag("model", "async-blocking").tag("api", "stock-info")
                    .register(meterRegistry));

            KrxDto.KrxStockInfoDto stockInfoDto = new KrxDto.KrxStockInfoDto();

            KrxStockInfo stockInfo = stockInfoDto.toEntity(
                    currentPerPbrOutputDto,
                    currentFinanceOutputDto,
                    currentKrxStockInfoDto
            );

            log.info("Saved stocks is : {}", stockCode);
            return CompletableFuture.completedFuture(Optional.of(stockInfo));
        } catch (Exception e) {
            log.error("Error fetching stock data for code {}: {}", stockCode, e.getMessage(), e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

}
