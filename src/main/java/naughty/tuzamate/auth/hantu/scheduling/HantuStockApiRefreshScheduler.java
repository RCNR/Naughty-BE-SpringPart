package naughty.tuzamate.auth.hantu.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncKrxService;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncNasdaqService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스케줄링 실행시키는 값은 application.yml에 있으며
 * enabled 값이 true인 경우에만 실행될 수 있도록 구성
 */

@RequiredArgsConstructor
@Component
@Slf4j
@ConditionalOnProperty(name = "hantu.stock.schedule.enabled", havingValue = "true")
public class HantuStockApiRefreshScheduler {

    private final AsyncKrxService krxService;
    private final AsyncNasdaqService asyncNasdaqService;

    @Scheduled(cron = "0 39 13 * * *")
    public void refreshStockData() {
        log.info("Hantu Stock API 데이터 갱신 시작");

        try {
            log.info("Hantu Krx API 데이터 갱신 시작");
            krxService.saveKrxStocksInfo();
            log.info("Hantu Krx API 데이터 갱신 완료");

            log.info("Hantu Nasdaq API 데이터 갱신 시작");
            asyncNasdaqService.saveNasdaqStocksInfo();
            log.info("Hantu Nasdaq API 데이터 갱신 완료");
            log.info("Hantu Stock API 데이터 갱신 완료");

        } catch (Exception e) {
            log.error("Hantu Stock API 데이터 갱신 중 오류 발생", e);
        }
    }
}
