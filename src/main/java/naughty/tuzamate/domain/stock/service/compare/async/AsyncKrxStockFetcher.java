package naughty.tuzamate.domain.stock.service.compare.async;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.service.common.KrxFinancialService;
import naughty.tuzamate.domain.stock.service.common.KrxInquireService;
import naughty.tuzamate.domain.stock.service.common.StockInfoService;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncKrxStockFetcher {

    private final KrxInquireService krxInquireService;
    private final KrxFinancialService krxFinancialService;
    private final StockInfoService stockInfoService;
    private final FilterStrategy filterStrategy;

    //    @SuppressWarnings("UnstableApiUsage")
//    private final RateLimiter rateLimiter = RateLimiter.create(12.0);
//
    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter = RateLimiter.create(15.0, 1,TimeUnit.SECONDS);

    /**
     * 3개의 API호출과 결과를 비동기 메소드로 묶는다.
     * StockCode를 받아 KrxStockInfo 엔티티를 CompleteableFuture로 반환한다.
     */
    @Async("taskExecutor") // 별도 쓰레드에서의 비동기 실행을 위한 어노테이션
    public CompletableFuture<Optional<KrxStockInfo>> fetchStock(String stockCode) {

        try {

//             한국 주식의 경우 주식 코드 하나 당 3번의 api 호출이므로 3개의 허가를 받는다.
//             초당 3개 종목 즉, 9회 호출을 처리한다.
            rateLimiter.acquire(3);
            // 위에처럼 했는데 시작하자마자 초당 거래 건수 초과로 에러가 뜨고 이후 잘 실행되지만 느리다

            // 주식 코드를 이용해 현재가, PER, PBR, 업종 한글 종목명 조회
            KrxDto.InquireDto currentPerPbrOutputDto = krxInquireService.getCurInquireInfo(stockCode);

            // 주식 코드를 이용해 EPS 값 조회
            KrxDto.FinancialDto currentFinanceOutputDto = krxFinancialService.getCurFinancialInfo(stockCode);

            StockInfoDto.InfoDto currentKrxStockInfoDto = stockInfoService.getStockInfo(stockCode, "300");

            if (filterStrategy.shouldSkipKrx(currentPerPbrOutputDto, currentFinanceOutputDto)) {
                log.info("PER or PBR or EPS is zero: {}", stockCode);
                return CompletableFuture.completedFuture(Optional.empty());
            }

            KrxDto.KrxStockInfoDto stockInfoDto = new KrxDto.KrxStockInfoDto();

            KrxStockInfo stockInfo = stockInfoDto.toEntity(
                    currentPerPbrOutputDto,
                    currentFinanceOutputDto,
                    currentKrxStockInfoDto
            );

            return CompletableFuture.completedFuture(Optional.of(stockInfo));
        } catch (Exception e) {
            log.error("Error fetching stock data for code {}: {}", stockCode, e.getMessage(), e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

}
