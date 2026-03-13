package naughty.tuzamate.domain.stock.service.compare.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.KrxStockInfoRepository;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncKrxService {

    private final StockCodeRepository stockCodeRepository;
    private final KrxStockInfoRepository krxStockInfoRepository;
    private final AsyncKrxStockFetcher asyncKrxStockFetcher;

    @Transactional
    public void saveKrxStocksInfo() {
        log.info("한국 주식 정보 저장/업데이트 시작");
        long start = System.currentTimeMillis();


        // 기존 데이터 삭제
        krxStockInfoRepository.deleteAllInBatch();

        List<StockCode> stockCodeList = stockCodeRepository.findAll();

        // 비동기 API 호출
        List<CompletableFuture<Optional<KrxStockInfo>>> completableFutures = stockCodeList.stream()
                .map(stockCode -> asyncKrxStockFetcher.fetchStock(stockCode.getCode()))
                .toList();

        log.info("{}개의 한국 주식 정보 요청 시작", stockCodeList.size());


        // 모든 CompletableFuture가 완료될 때까지 기다린다
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join(); // join은 예외를 던지지 않는다
        log.info("모든 한국 주식 정보 요청 완료");

        // 결과를 Optional<KrxStockInfo>로 변환하여 리스트로 수집한다
        List<KrxStockInfo> stockInfoList = completableFutures.stream()
                .map(CompletableFuture::join) // CompletableFuture에서 결과를 가져온다 -> Optional<KrxStockInfo>
                .filter(Optional::isPresent) // 비어있지 않은 경우만
                .map(Optional::get).toList();// Optional에서 KrxStockInfo 가져옴

        if(!stockInfoList.isEmpty()) {
            log.info("{} 개의 한국 주식 정보를 DB에 저장 시작", stockInfoList.size());
            krxStockInfoRepository.saveAll(stockInfoList);
        }

        long end = System.currentTimeMillis();
        log.info("한국 주식 정보 저장/업데이트 완료, 소요 시간: {} ms", (end - start));

    }
}
