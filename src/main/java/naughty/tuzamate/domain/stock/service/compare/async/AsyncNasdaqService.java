package naughty.tuzamate.domain.stock.service.compare.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.entity.NasdaqStockCode;
import naughty.tuzamate.domain.stock.entity.NasdaqStockInfo;
import naughty.tuzamate.domain.stock.repository.NasdaqStockInfoRepository;
import naughty.tuzamate.domain.stock.repository.code.NasdaqCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNasdaqService {

    private final NasdaqCodeRepository nasdaqCodeRepository;
    private final NasdaqStockInfoRepository nasdaqStockInfoRepository;
    private final AsyncNasdaqStockFetcher asyncNasdaqStockFetcher;

    @Transactional
    public void saveNasdaqStocksInfo() {
        log.info("미국 주식 정보 저장/업데이트 시작");
        long start = System.currentTimeMillis();

        // 기존 데이터 삭제
        nasdaqStockInfoRepository.deleteAllInBatch();

        List<NasdaqStockCode> stockCodeList = nasdaqCodeRepository.findAll();

        // 비동기 API 호출
        List<CompletableFuture<Optional<NasdaqStockInfo>>> completableFutures = stockCodeList.stream()
                .map(stockCode -> asyncNasdaqStockFetcher.fetchStock(stockCode.getCode()))
                .toList();

        log.info("{}개의 나스닥 주식 정보 요청 시작", stockCodeList.size());

        // 모든 CompletableFuture가 완료될 때까지 기다린다
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
        log.info("모든 나스닥 주식 정보 요청 완료");

        // 결과를 Optional<NasdaqStockInfo>로 변환하여 리스트로 수집한다
        List<NasdaqStockInfo> stockInfoList = completableFutures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // 데이터 DB에 일괄 저장
        if (!stockInfoList.isEmpty()) {
            log.info("{} 개의 나스닥 주식 정보를 DB에 저장 시작", stockInfoList.size());
            nasdaqStockInfoRepository.saveAll(stockInfoList);
        }

        long end = System.currentTimeMillis();
        log.info("나스닥 주식 정보 저장/업데이트 완료, 소요 시간: {} ms", (end - start));

    }
}
